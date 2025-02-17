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
package io.camunda.client.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.impl.util.Environment;
import io.camunda.client.impl.util.EnvironmentExtension;
import java.util.Properties;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EnvironmentExtension.class)
class BuilderUtilsTest {
  private Properties properties;
  private Consumer<String> action;

  @BeforeEach
  void setup() {
    properties = new Properties();
    action = mock(Consumer.class);
  }

  @Test
  void shouldApplyActionOnFirstNonNullPropertyValue() {
    // given
    properties.setProperty("property1", "value1");
    properties.setProperty("property2", "value2");

    // when
    BuilderUtils.applyPropertyValueIfNotNull(properties, action, "property1", "property2");

    // then
    verify(action).accept("value1");
    verifyNoMoreInteractions(action);
  }

  @Test
  void shouldSkipNullPropertyValues() {
    // given
    properties.setProperty("property2", "value2");

    // when
    BuilderUtils.applyPropertyValueIfNotNull(properties, action, "property1", "property2");

    // then
    verify(action).accept("value2");
    verifyNoMoreInteractions(action);
  }

  @Test
  void shouldNotApplyActionIfNoPropertyValuesFound() {
    // when
    BuilderUtils.applyPropertyValueIfNotNull(properties, action, "property1", "property2");

    // then
    verifyNoInteractions(action);
  }

  @Test
  void shouldApplyActionOnFirstNonNullEnvironmentVariableValue() {
    // given
    Environment.system().put("env1", "value1");
    Environment.system().put("env2", "value2");
    // when
    BuilderUtils.applyEnvironmentValueIfNotNull(action, "env1", "env2");

    // then
    verify(action).accept("value1");
    verifyNoMoreInteractions(action);
  }

  @Test
  void shouldSkipNullEnvironmentValues() {
    // given
    Environment.system().put("env2", "value2");
    // when
    BuilderUtils.applyEnvironmentValueIfNotNull(action, "env1", "env2");

    // then
    verify(action).accept("value2");
    verifyNoMoreInteractions(action);
  }

  @Test
  void shouldNotApplyActionIfNoEnvironmentVariablesFound() {
    // when
    BuilderUtils.applyEnvironmentValueIfNotNull(action, "env1", "env2");

    // then
    verifyNoInteractions(action);
  }
}
