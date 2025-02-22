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
package com.thoughtworks.go.util;

import com.thoughtworks.go.config.GoConfigSchema;
import org.jdom2.input.JDOMParseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static com.thoughtworks.go.util.XmlUtils.buildValidatedXmlDocument;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class XmlUtilsTest {

    @Test
    public void shouldThrowExceptionWithTranslatedErrorMessage() {
        String xmlContent = "<foo name='invalid'/>";
        assertThatThrownBy(() -> XmlUtils.buildValidatedXmlDocument(new ByteArrayInputStream(xmlContent.getBytes()), GoConfigSchema.getCurrentSchema()))
                .isInstanceOf(XsdValidationException.class);
    }

    @Test
    public void shouldThrowExceptionWhenXmlIsMalformed() {
        String xmlContent = "<foo name='invalid'";
        assertThatThrownBy(() -> buildValidatedXmlDocument(new ByteArrayInputStream(xmlContent.getBytes()), GoConfigSchema.getCurrentSchema()))
                .isInstanceOf(JDOMParseException.class)
                .hasMessageContaining("Error on line 1: XML document structures must start and end within the same entity");
    }
}
