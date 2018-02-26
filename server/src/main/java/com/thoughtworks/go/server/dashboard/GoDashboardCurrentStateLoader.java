/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.server.dashboard;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.security.GoConfigPipelinePermissionsAuthority;
import com.thoughtworks.go.config.security.Permissions;
import com.thoughtworks.go.config.security.users.NoOne;
import com.thoughtworks.go.domain.PipelineGroupVisitor;
import com.thoughtworks.go.domain.PipelinePauseInfo;
import com.thoughtworks.go.domain.PiplineConfigVisitor;
import com.thoughtworks.go.presentation.pipelinehistory.PipelineInstanceModel;
import com.thoughtworks.go.presentation.pipelinehistory.PipelineInstanceModels;
import com.thoughtworks.go.presentation.pipelinehistory.PipelineModel;
import com.thoughtworks.go.presentation.pipelinehistory.StageInstanceModels;
import com.thoughtworks.go.server.dao.PipelineDao;
import com.thoughtworks.go.server.scheduling.TriggerMonitor;
import com.thoughtworks.go.server.service.PipelineLockService;
import com.thoughtworks.go.server.service.PipelinePauseService;
import com.thoughtworks.go.server.service.PipelineUnlockApiService;
import com.thoughtworks.go.server.service.SchedulingCheckerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.thoughtworks.go.config.CaseInsensitiveString.str;
import static com.thoughtworks.go.domain.buildcause.BuildCause.createWithEmptyModifications;
import static com.thoughtworks.go.presentation.pipelinehistory.PipelineInstanceModel.createEmptyPipelineInstanceModel;
import static com.thoughtworks.go.presentation.pipelinehistory.PipelineInstanceModel.createPreparingToSchedule;
import static com.thoughtworks.go.presentation.pipelinehistory.PipelineInstanceModels.createPipelineInstanceModels;

/* Understands the current state of a pipeline, which is to be shown on the dashboard. */
@Component
public class GoDashboardCurrentStateLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoDashboardCurrentStateLoader.class);
    private PipelineDao pipelineDao;
    private TriggerMonitor triggerMonitor;
    private PipelinePauseService pipelinePauseService;
    private PipelineLockService pipelineLockService;
    private PipelineUnlockApiService pipelineUnlockApiService;
    private SchedulingCheckerService schedulingCheckerService;
    private GoConfigPipelinePermissionsAuthority permissionsAuthority;
    private TimeStampBasedCounter timeStampBasedCounter;

    @Autowired
    public GoDashboardCurrentStateLoader(PipelineDao pipelineDao, TriggerMonitor triggerMonitor,
                                         PipelinePauseService pipelinePauseService, PipelineLockService pipelineLockService,
                                         PipelineUnlockApiService pipelineUnlockApiService, SchedulingCheckerService schedulingCheckerService,
                                         GoConfigPipelinePermissionsAuthority permissionsAuthority, TimeStampBasedCounter timeStampBasedCounter) {
        this.pipelineDao = pipelineDao;
        this.triggerMonitor = triggerMonitor;
        this.pipelinePauseService = pipelinePauseService;
        this.pipelineLockService = pipelineLockService;
        this.pipelineUnlockApiService = pipelineUnlockApiService;
        this.schedulingCheckerService = schedulingCheckerService;
        this.permissionsAuthority = permissionsAuthority;
        this.timeStampBasedCounter = timeStampBasedCounter;
    }

    public List<GoDashboardPipeline> allPipelines(CruiseConfig config) {
        List<String> pipelineNames = CaseInsensitiveString.toStringList(config.getAllPipelineNames());
        PipelineInstanceModels historyForDashboard = loadHistoryForPipelines(pipelineNames);

        LOGGER.debug("Loading permissions from authority");
        final Map<CaseInsensitiveString, Permissions> pipelinesAndTheirPermissions = permissionsAuthority.pipelinesAndTheirPermissions();

        final List<GoDashboardPipeline> pipelines = new ArrayList<>(1024);

        LOGGER.debug("Populating dashboard pipelines");
        config.accept(new PipelineGroupVisitor() {
            @Override
            public void visit(final PipelineConfigs group) {
                group.accept(new PiplineConfigVisitor() {
                    @Override
                    public void visit(PipelineConfig pipelineConfig) {
                        long start = System.currentTimeMillis();
                        Permissions permissions = permissionsFor(pipelineConfig, pipelinesAndTheirPermissions);

                        pipelines.add(createGoDashboardPipeline(pipelineConfig, permissions, historyForDashboard, group));

                        LOGGER.debug("It took {}ms to process pipeline {}", (System.currentTimeMillis() - start), pipelineConfig.getName());
                    }
                });
            }
        });
        LOGGER.debug("Done populating dashboard pipelines");
        return pipelines;
    }

    private PipelineInstanceModels loadHistoryForPipelines(List<String> pipelineNames) {
        LOGGER.debug("Loading history for dashboard with {} pipelines", pipelineNames.size());
        try {
            return pipelineDao.loadHistoryForDashboard(pipelineNames);
        } finally {
            LOGGER.debug("Done loading history for dashboard");
        }
    }

    public GoDashboardPipeline pipelineFor(PipelineConfig pipelineConfig, PipelineConfigs groupConfig) {
        List<String> pipelineNames = CaseInsensitiveString.toStringList(Collections.singletonList(pipelineConfig.getName()));
        PipelineInstanceModels historyForDashboard = loadHistoryForPipelines(pipelineNames);

        Permissions permissions = permissionsAuthority.permissionsForPipeline(pipelineConfig.name());
        return createGoDashboardPipeline(pipelineConfig, permissions, historyForDashboard, groupConfig);
    }

    private GoDashboardPipeline createGoDashboardPipeline(PipelineConfig pipelineConfig, Permissions permissions, PipelineInstanceModels historyForDashboard, PipelineConfigs group) {
        PipelineModel pipelineModel = pipelineModelFor(pipelineConfig, historyForDashboard);
        Optional<TrackingTool> trackingTool = pipelineConfig.getIntegratedTrackingTool();
        return new GoDashboardPipeline(pipelineModel, permissions, group.getGroup(), trackingTool.orElse(null), timeStampBasedCounter);
    }

    private PipelineModel pipelineModelFor(PipelineConfig pipelineConfig, PipelineInstanceModels historyForDashboard) {
        String pipelineName = str(pipelineConfig.name());

        PipelinePauseInfo pauseInfo = pipelinePauseService.pipelinePauseInfo(pipelineName);
        boolean canBeForced = schedulingCheckerService.pipelineCanBeTriggeredManually(pipelineConfig);

        PipelineModel pipelineModel = new PipelineModel(pipelineName, canBeForced, true, pauseInfo);
        pipelineModel.updateAdministrability(pipelineConfig.isLocal());

        pipelineModel.addPipelineInstances(instancesFor(pipelineConfig, historyForDashboard));
        return pipelineModel;
    }

    private PipelineInstanceModels instancesFor(PipelineConfig pipelineConfig, PipelineInstanceModels historyForDashboard) {
        PipelineInstanceModels pims = findPIMsWithFallbacks(pipelineConfig, historyForDashboard);

        boolean isCurrentlyLocked = pipelineLockService.isLocked(str(pipelineConfig.name()));
        boolean isUnlockable = pipelineUnlockApiService.isUnlockable(str(pipelineConfig.name()));

        for (PipelineInstanceModel instanceModel : pims) {
            populateStagesWhichHaventRunFromConfig(instanceModel, pipelineConfig);
            populateLockStatus(instanceModel, pipelineConfig.isLockable(), isCurrentlyLocked, isUnlockable);
        }

        return pims;
    }

    private PipelineInstanceModels findPIMsWithFallbacks(PipelineConfig pipelineConfig, PipelineInstanceModels historyForDashboard) {
        String pipelineName = str(pipelineConfig.name());

        PipelineInstanceModels pipelinesToShow = historyForDashboard.findAll(pipelineName);
        if (!pipelinesToShow.isEmpty()) {
            return pipelinesToShow;
        }

        if (triggerMonitor.isAlreadyTriggered(pipelineName)) {
            return createPipelineInstanceModels(createPreparingToSchedule(pipelineName, new StageInstanceModels()));
        }

        return createPipelineInstanceModels(createEmptyPipelineInstanceModel(pipelineName, createWithEmptyModifications(), new StageInstanceModels()));
    }

    private void populateStagesWhichHaventRunFromConfig(PipelineInstanceModel instanceModel, PipelineConfig pipelineConfig) {
        instanceModel.getStageHistory().updateFutureStagesFrom(pipelineConfig);
    }

    /* TODO: This belongs in PipelineModel, not in PIM */
    private void populateLockStatus(PipelineInstanceModel instanceModel, boolean isLockable, boolean isCurrentlyLocked, boolean canBeUnlocked) {
        instanceModel.setIsLockable(isLockable);
        instanceModel.setCurrentlyLocked(isCurrentlyLocked);
        instanceModel.setCanUnlock(canBeUnlocked);
    }

    private Permissions permissionsFor(PipelineConfig pipelineConfig, Map<CaseInsensitiveString, Permissions> pipelinesAndTheirPermissions) {
        if (pipelinesAndTheirPermissions.containsKey(pipelineConfig.name())) {
            return pipelinesAndTheirPermissions.get(pipelineConfig.name());
        }
        return new Permissions(NoOne.INSTANCE, NoOne.INSTANCE, NoOne.INSTANCE, NoOne.INSTANCE);
    }
}
