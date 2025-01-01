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
package com.thoughtworks.go.plugin.activation.test;

import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.TestGoPluginExtensionPoint;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import java.util.List;

public class ClassThatExtendsTestExtensionPoint implements TestGoPluginExtensionPoint {
    @Extension
    public static class ClassThatExtendsTwoGoExtensionPoint extends ClassThatExtendsTestExtensionPoint implements GoPlugin {
        @Override
        public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GoPluginApiResponse handle(GoPluginApiRequest requestMessage) throws UnhandledRequestTypeException {
            throw new UnsupportedOperationException();
        }

        @Override
        public GoPluginIdentifier pluginIdentifier() {
            return new GoPluginIdentifier("some-extension-type", List.of("1.0"));
        }
    }

}
