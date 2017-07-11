/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.test.util;

import static org.mockito.Mockito.withSettings;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import org.mockito.Mockito;
import org.mockito.internal.configuration.DefaultAnnotationEngine;

public class ZeebeMockitoAnnotationEngine extends DefaultAnnotationEngine
{

    @Override
    public Object createMockFor(Annotation annotation, Field field)
    {
        if (annotation instanceof FluentMock)
        {
            return Mockito.mock(field.getType(),
                    withSettings()
                        .name(field.getName())
                        .defaultAnswer(new FluentAnswer()));
        }
        else
        {
            return super.createMockFor(annotation, field);
        }
    }
}
