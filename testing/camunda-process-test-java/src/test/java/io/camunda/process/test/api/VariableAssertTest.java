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
package io.camunda.process.test.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.VariableBuilder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class VariableAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;
  private static final Map<String, Object> CONTEXT_VARIABLE_VALUE;

  static {
    CONTEXT_VARIABLE_VALUE = new HashMap<>();
    CONTEXT_VARIABLE_VALUE.put("a", 1);
    CONTEXT_VARIABLE_VALUE.put("b", 2);
  }

  @Mock private CamundaDataSource camundaDataSource;
  @Mock private ProcessInstanceEvent processInstanceEvent;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
    CamundaAssert.setAssertionInterval(Duration.ZERO);
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(1));
  }

  @AfterEach
  void resetAssertions() {
    CamundaAssert.setAssertionInterval(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
  }

  @BeforeEach
  void configureMocks() {
    when(camundaDataSource.findProcessInstances(any()))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
  }

  private static Variable newVariable(final String variableName, final String variableValue) {
    return VariableBuilder.newVariable(variableName, variableValue)
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .build();
  }

  private static Stream<Arguments> variableValues() {
    return Stream.of(
        Arguments.of("null", null),
        Arguments.of("1", 1),
        Arguments.of("1.5", 1.5),
        Arguments.of("\"a\"", "a"),
        Arguments.of("true", true),
        Arguments.of("[1,2]", Arrays.asList(1, 2)),
        Arguments.of("{\"a\":1,\"b\":2}", CONTEXT_VARIABLE_VALUE),
        Arguments.of("{\"b\":2,\"a\":1}", CONTEXT_VARIABLE_VALUE));
  }

  @Nested
  class HasVariableNames {

    @Test
    void shouldHasVariableNames() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasVariableNames("a", "b");
    }

    @Test
    void shouldWaitUntilHasVariableNames() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasVariableNames("a", "b");

      verify(camundaDataSource, times(2)).findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfVariableNotExist() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasVariableNames("a", "b", "c", "d"))
          .hasMessage(
              "Process instance [key: %d] should have the variables ['a', 'b', 'c', 'd'] but ['c', 'd'] don't exist.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariableNames("a"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasVariable {

    @ParameterizedTest
    @MethodSource("io.camunda.process.test.api.VariableAssertTest#variableValues")
    void shouldHasVariable(final String variableValue, final Object expectedValue) {
      // given
      final Variable variableA = newVariable("a", variableValue);

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasVariable("a", expectedValue);
    }

    @Test
    void shouldWaitUntilHasVariable() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableB))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasVariable("a", 1);

      verify(camundaDataSource, times(2)).findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilVariableHasValue() {
      // given
      final Variable variableValue1 = newVariable("a", "1");
      final Variable variableValue2 = newVariable("a", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableValue1))
          .thenReturn(Collections.singletonList(variableValue2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasVariable("a", 2);

      verify(camundaDataSource, times(2)).findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfVariableNotExist() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariable("c", 3))
          .hasMessage(
              "Process instance [key: %d] should have a variable 'c' with value '3' but the variable doesn't exist.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfVariableHasDifferentValue() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariable("a", 2))
          .hasMessage(
              "Process instance [key: %d] should have a variable 'a' with value '2' but was '1'.",
              PROCESS_INSTANCE_KEY);
    }

    @ParameterizedTest
    @MethodSource("io.camunda.process.test.api.VariableAssertTest#variableValues")
    void shouldFailWithMessage(final String variableValue) {
      // given
      final Variable variableA = newVariable("a", variableValue);

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariable("a", -1))
          .hasMessage(
              "Process instance [key: %d] should have a variable 'a' with value '-1' but was '%s'.",
              PROCESS_INSTANCE_KEY, variableValue, variableValue);
    }

    @Test
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariable("a", "1"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasVariables {

    @ParameterizedTest
    @MethodSource("io.camunda.process.test.api.VariableAssertTest#variableValues")
    void shouldHasVariables(final String variableValue, final Object expectedValue) {
      // given
      final Variable variableA = newVariable("a", variableValue);
      final Variable variableB = newVariable("b", "100");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", expectedValue);
      expectedVariables.put("b", 100);
      CamundaAssert.assertThat(processInstanceEvent).hasVariables(expectedVariables);
    }

    @Test
    void shouldWaitUntilHasAllVariables() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);
      expectedVariables.put("b", 2);
      CamundaAssert.assertThat(processInstanceEvent).hasVariables(expectedVariables);

      verify(camundaDataSource, times(2)).findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilAllVariablesHaveValue() {
      // given
      final Variable variableValue1 = newVariable("a", "1");
      final Variable variableValue2 = newVariable("a", "2");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableValue1, variableB))
          .thenReturn(Arrays.asList(variableValue2, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 2);
      expectedVariables.put("b", 2);
      CamundaAssert.assertThat(processInstanceEvent).hasVariables(expectedVariables);

      verify(camundaDataSource, times(2)).findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfOneVariableNotExist() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);
      expectedVariables.put("c", 3);

      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariables(expectedVariables))
          .hasMessage(
              "Process instance [key: %d] should have the variables {\"a\":1,\"c\":3} but was {\"a\":1}. The variables ['c'] don't exist.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfVariableHasDifferentValue() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");
      final Variable variableC = newVariable("c", "3");

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB, variableC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);
      expectedVariables.put("b", 1);

      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariables(expectedVariables))
          .hasMessage(
              "Process instance [key: %d] should have the variables {\"a\":1,\"b\":1} but was {\"a\":1,\"b\":2}.",
              PROCESS_INSTANCE_KEY);
    }

    @ParameterizedTest
    @MethodSource("io.camunda.process.test.api.VariableAssertTest#variableValues")
    void shouldFailWithMessage(final String variableValue) {
      // given
      final Variable variableA = newVariable("a", variableValue);

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", -1);

      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariables(expectedVariables))
          .hasMessage(
              "Process instance [key: %d] should have the variables {\"a\":-1} but was {\"a\":%s}.",
              PROCESS_INSTANCE_KEY, variableValue);
    }

    @Test
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);

      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasVariables(expectedVariables))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }
}
