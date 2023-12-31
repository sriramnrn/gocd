/*
 * Copyright 2024 Thoughtworks, Inc.
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
package com.thoughtworks.go.spark.spa;

import com.thoughtworks.go.spark.Routes;
import com.thoughtworks.go.spark.SparkController;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.TemplateEngine;

import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.path;

public class KitchenSinkController implements SparkController {
    private final TemplateEngine engine;

    public KitchenSinkController(TemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public String controllerBasePath() {
        return Routes.KitchenSink.SPA_BASE;
    }

    @Override
    public void setupRoutes() {
        path(controllerBasePath(), () -> get("", this::index, engine));
    }

    private ModelAndView index(Request request, Response response) {
        Map<String, Object> object = Map.of(
            "viewTitle", "Kitchen Sink"
        );

        return new ModelAndView(object, null);
    }
}
