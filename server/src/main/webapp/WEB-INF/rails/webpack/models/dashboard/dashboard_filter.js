/*
 * Copyright Thoughtworks, Inc.
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
import _ from "lodash";

export function DashboardFilter(config) {
  this.acceptsStatusOf = (pipeline) => {
    const latestStage = pipeline.latestStage();

    if (config.state.length) {
      if (pipeline.isPaused && _.includes(config.state, "paused")) {
        return true;
      }
      if (!latestStage) { return false; }
      if (latestStage.isBuilding() || latestStage.isFailing()) { return _.includes(config.state, "building"); }
      if (latestStage.isFailed()) { return _.includes(config.state, "failing"); }
      if (latestStage.isCancelled()) { return _.includes(config.state, "cancelled"); }
      return false;
    }

    return true;
  };
}
