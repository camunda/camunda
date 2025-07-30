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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.assertions.util.AssertionJsonMapper;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import io.camunda.process.test.utils.ElementInstanceBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.VariableBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
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

  // Used to test assertVariableSatisfies JSON logic
  private static final class SimpleJsonObject {

    @JsonProperty("string")
    private String strValue;

    @JsonProperty("int")
    private int intValue;

    @JsonProperty("boolean")
    private boolean boolValue;

    @JsonProperty("list")
    private List<Object> list;

    @JsonProperty("object")
    private SimpleJsonNestedObject nestedObject;
  }

  private static final class SimpleJsonNestedObject {

    @JsonProperty private String key;
  }

  @Nested
  class HasVariableNames {

    @Test
    void shouldHasVariableNames() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariableNames("a", "b");
    }

    @Test
    void shouldPassIfHasVariableNamesIncludesNullValues() {
      // given
      final Variable variableA = newVariable("a", null);
      final Variable variableB = newVariable("b", null);

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariableNames("a", "b");
    }

    @Test
    void shouldWaitUntilHasVariableNames() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariableNames("a", "b");

      verify(camundaDataSource, times(2))
          .findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfVariableNotExist() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableNames("a", "b", "c", "d"))
          .hasMessage(
              "Process instance [key: %d] should have the variables ['a', 'b', 'c', 'd'] but ['c', 'd'] don't exist.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableNames("a"))
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

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariable("a", expectedValue);
    }

    @Test
    void shouldWaitUntilHasVariable() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableB))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariable("a", 1);

      verify(camundaDataSource, times(2))
          .findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldPassIfHasVariableContainsNullValues() {
      // given
      final Variable variableWithNull = newVariable("a", null);

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableWithNull));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariable("a", null);
    }

    @Test
    void shouldWaitUntilVariableHasValue() {
      // given
      final Variable variableValue1 = newVariable("a", "1");
      final Variable variableValue2 = newVariable("a", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableValue1))
          .thenReturn(Collections.singletonList(variableValue2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariable("a", 2);

      verify(camundaDataSource, times(2))
          .findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfVariableNotExist() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariable("c", 3))
          .hasMessage(
              "Process instance [key: %d] should have a variable 'c' with value '3' but the variable doesn't exist.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfVariableHasDifferentValue() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariable("a", 2))
          .hasMessage(
              "Process instance [key: %d] should have a variable 'a' with value '2' but was '1'.",
              PROCESS_INSTANCE_KEY);
    }

    @ParameterizedTest
    @MethodSource("io.camunda.process.test.api.VariableAssertTest#variableValues")
    @CamundaAssertExpectFailure
    void shouldFailWithMessage(final String variableValue) {
      // given
      final Variable variableA = newVariable("a", variableValue);

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariable("a", -1))
          .hasMessage(
              "Process instance [key: %d] should have a variable 'a' with value '-1' but was '%s'.",
              PROCESS_INSTANCE_KEY, variableValue, variableValue);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariable("a", "1"))
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

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", expectedValue);
      expectedVariables.put("b", 100);
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);
    }

    @Test
    void shouldPassIfHasVariablesContainsNullValues() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable nullVariableB = newVariable("b", null);
      final Variable nullVariableC = newVariable("c", null);

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, nullVariableB, nullVariableC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);
      expectedVariables.put("b", null);
      expectedVariables.put("c", null);
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);
    }

    @Test
    void shouldWaitUntilHasAllVariables() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);
      expectedVariables.put("b", 2);
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);

      verify(camundaDataSource, times(2))
          .findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilAllVariablesHaveValue() {
      // given
      final Variable variableValue1 = newVariable("a", "1");
      final Variable variableValue2 = newVariable("a", "2");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableValue1, variableB))
          .thenReturn(Arrays.asList(variableValue2, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 2);
      expectedVariables.put("b", 2);
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);

      verify(camundaDataSource, times(2))
          .findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfOneVariableNotExist() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);
      expectedVariables.put("c", 3);

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariables(expectedVariables))
          .hasMessage(
              "Process instance [key: %d] should have the variables {\"a\":1,\"c\":3} but was {\"a\":1}. The variables ['c'] don't exist.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfVariableHasDifferentValue() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");
      final Variable variableC = newVariable("c", "3");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(variableA, variableB, variableC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);
      expectedVariables.put("b", 1);

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariables(expectedVariables))
          .hasMessage(
              "Process instance [key: %d] should have the variables {\"a\":1,\"b\":1} but was {\"a\":1,\"b\":2}.",
              PROCESS_INSTANCE_KEY);
    }

    @ParameterizedTest
    @MethodSource("io.camunda.process.test.api.VariableAssertTest#variableValues")
    @CamundaAssertExpectFailure
    void shouldFailWithMessage(final String variableValue) {
      // given
      final Variable variableA = newVariable("a", variableValue);

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableA));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", -1);

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariables(expectedVariables))
          .hasMessage(
              "Process instance [key: %d] should have the variables {\"a\":-1} but was {\"a\":%s}.",
              PROCESS_INSTANCE_KEY, variableValue);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("a", 1);

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariables(expectedVariables))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class EdgeCase {

    @Test
    void shouldAssertOnTruncatedVariableIfFetchFails() {
      // given
      final Variable truncatedVariable =
          VariableBuilder.newVariable("largeVar", "\"truncatedValue\"")
              .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
              .setTruncated(true)
              .setVariableKey(100L)
              .build();

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(truncatedVariable));

      when(camundaDataSource.getVariable(100L)).thenThrow(RuntimeException.class);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("largeVar", "truncatedValue");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);
    }
  }

  @Nested
  class HasVariableSatisfies {

    private static final String COMPLEX_VARIABLE_KEY = "complex";
    private static final String COMPLEX_VARIABLE_VALUE =
        "{\"string\":\"value\",\"int\":2,\"boolean\":true,\"list\":[\"a\",1,true,null,[\"foo\"]],\"object\":{\"key\":\"value\"}}";

    private final Variable complexVariable =
        newVariable(COMPLEX_VARIABLE_KEY, COMPLEX_VARIABLE_VALUE);

    private final ThrowingConsumer<SimpleJsonObject> assertionRequirements =
        result -> {
          Assertions.assertThat(result).isNotNull();

          Assertions.assertThat(result)
              .extracting("strValue", "intValue", "boolValue")
              .containsExactlyInAnyOrder("value", 2, true);

          Assertions.assertThat(result)
              .extracting("list", InstanceOfAssertFactories.list(Object.class))
              .containsExactlyInAnyOrder(1, "a", true, null, Collections.singletonList("foo"));

          Assertions.assertThat(result).extracting("nestedObject.key").isEqualTo("value");
        };

    @Test
    @SuppressWarnings("unchecked")
    void shouldSatisfyConditions() {
      // given
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(complexVariable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSatisfies(
              COMPLEX_VARIABLE_KEY,
              Map.class,
              result -> {
                Assertions.assertThat(result).hasSize(5);
                Assertions.assertThat(result)
                    .containsEntry("string", "value")
                    .containsEntry("int", 2)
                    .containsEntry("boolean", true);
                Assertions.assertThat(result)
                    .extracting("list", InstanceOfAssertFactories.list(Object.class))
                    .asList()
                    .containsExactlyInAnyOrder(
                        1, "a", true, null, Collections.singletonList("foo"));
              });
    }

    @Test
    void shouldSatisfyConditionsForLocalVariables() {
      // given
      final String elementId = "element-id";
      final String elementName = "Element Name";
      final ElementInstance elementInstanceA =
          ElementInstanceBuilder.newActiveElementInstance(elementId, PROCESS_INSTANCE_KEY)
              .setElementName(elementName)
              .setElementInstanceKey(1L)
              .build();

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(elementInstanceA));
      when(camundaDataSource.findVariables(any()))
          .thenReturn(Collections.singletonList(complexVariable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasLocalVariableSatisfies(
              elementId, COMPLEX_VARIABLE_KEY, SimpleJsonObject.class, assertionRequirements);

      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasLocalVariableSatisfies(
              ElementSelectors.byId(elementId),
              COMPLEX_VARIABLE_KEY,
              SimpleJsonObject.class,
              assertionRequirements);

      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasLocalVariableSatisfies(
              ElementSelectors.byName(elementName),
              COMPLEX_VARIABLE_KEY,
              SimpleJsonObject.class,
              assertionRequirements);
    }

    @Test
    void shouldSatisfyConditionsWithJsonDeserialization() {
      // given
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(complexVariable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSatisfies(
              COMPLEX_VARIABLE_KEY, SimpleJsonObject.class, assertionRequirements);
    }

    @Test
    void shouldWaitUntilVariableExists() {
      // given
      final Variable variableA = newVariable("a", "1");
      final Variable variableB = newVariable("b", "2");

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variableB))
          .thenReturn(Arrays.asList(variableA, variableB));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSatisfies(
              "a", String.class, value -> Assertions.assertThat(value).isEqualTo("1"));

      verify(camundaDataSource, times(2))
          .findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldHaveSensibleErrorMessageWhenAssertionFails() {
      // given
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(complexVariable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSatisfies(
                          COMPLEX_VARIABLE_KEY,
                          SimpleJsonObject.class,
                          result -> {
                            Assertions.assertThat(result)
                                .describedAs("Should be not null")
                                .isNull();
                            Assertions.assertThat(result)
                                .extracting("strValue", "intValue", "boolValue")
                                .containsExactlyInAnyOrder("wrong", -1, false);
                            Assertions.assertThat(result)
                                .extracting("list", InstanceOfAssertFactories.list(Object.class))
                                .containsExactlyInAnyOrder(
                                    -10, "wrong", false, "truthy", Collections.emptyList());
                            Assertions.assertThat(result)
                                .extracting("nestedObject.key")
                                .isEqualTo("wrong_value");
                          }))
          .hasMessageContainingAll(
              "Process instance [key: 1] should have a variable 'complex' but the following requirement was not satisfied:",
              "[Should be not null]",
              "expected: null",
              "but was: io.camunda.process.test.api.VariableAssertTest$SimpleJsonObject");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldHaveSensibleErrorMessageWhenJsonMappingFails() {
      // given
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(complexVariable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSatisfies(
                          COMPLEX_VARIABLE_KEY,
                          List.class,
                          result -> Assertions.assertThat(result).hasSize(5)))
          .hasMessage(
              "Process instance [key: 1] should have a variable 'complex' of type 'java.util.List', but was: '"
                  + AssertionJsonMapper.readJson(COMPLEX_VARIABLE_VALUE)
                  + "'");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldHaveSensibleErrorMessageWhenNoVariablesFound() {
      // given
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.emptyList());
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSatisfies(
                          COMPLEX_VARIABLE_KEY,
                          SimpleJsonObject.class,
                          result -> Assertions.assertThat(result).isNotNull()))
          .hasMessage(
              "Process instance [key: 1] should have a variable 'complex', but the variable doesn't exist.");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldConvertCheckedExceptions() {
      // given
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(complexVariable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSatisfies(
                          COMPLEX_VARIABLE_KEY,
                          SimpleJsonObject.class,
                          result -> {
                            throw new Exception("Error");
                          }))
          .hasMessage("java.lang.Exception: Error");
    }
  }
}
