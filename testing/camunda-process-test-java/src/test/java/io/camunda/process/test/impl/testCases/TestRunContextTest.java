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
package io.camunda.process.test.impl.testCases;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestRunContextTest {

  private static final String RUN_ID = "run-1";

  @Test
  void shouldAddRunIdWhenEnabled() {
    // given
    final TestRunContext runContext = TestRunContext.enabled(RUN_ID);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", 1);

    // when
    final Map<String, Object> result = runContext.applyRunId(variables);

    // then
    assertThat(result)
        .containsEntry("a", 1)
        .containsEntry(TestRunContext.RUN_ID_VARIABLE_NAME, RUN_ID);
  }

  @Test
  void shouldNotMutateInputVariables() {
    // given
    final TestRunContext runContext = TestRunContext.enabled(RUN_ID);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", 1);

    // when
    runContext.applyRunId(variables);

    // then
    assertThat(variables).doesNotContainKey(TestRunContext.RUN_ID_VARIABLE_NAME);
  }

  @Test
  void shouldHandleNullVariablesWhenEnabled() {
    // given
    final TestRunContext runContext = TestRunContext.enabled(RUN_ID);

    // when
    final Map<String, Object> result = runContext.applyRunId(null);

    // then
    assertThat(result).hasSize(1).containsEntry(TestRunContext.RUN_ID_VARIABLE_NAME, RUN_ID);
  }

  @Test
  void shouldReturnVariablesUnchangedWhenDisabled() {
    // given
    final TestRunContext runContext = TestRunContext.disabled();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", 1);

    // when
    final Map<String, Object> result = runContext.applyRunId(variables);

    // then
    assertThat(result).isSameAs(variables);
    assertThat(runContext.isEnabled()).isFalse();
  }

  @Test
  void shouldReadEnabledFlagFromSystemProperty() {
    // given
    System.setProperty(TestRunContext.ENABLED_PROPERTY, "true");
    try {
      // when
      final TestRunContext runContext = TestRunContext.fromSystemProperties();

      // then
      assertThat(runContext.isEnabled()).isTrue();
      assertThat(runContext.getRunId()).isNotBlank();
    } finally {
      System.clearProperty(TestRunContext.ENABLED_PROPERTY);
    }
  }

  @Test
  void shouldBeDisabledByDefault() {
    // when
    final TestRunContext runContext = TestRunContext.fromSystemProperties();

    // then
    assertThat(runContext.isEnabled()).isFalse();
  }
}
