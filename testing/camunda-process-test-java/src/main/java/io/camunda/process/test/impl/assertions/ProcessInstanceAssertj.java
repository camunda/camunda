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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
import io.camunda.process.test.impl.client.FlowNodeInstanceState;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.ProcessInstanceState;
import io.camunda.process.test.impl.client.VariableDto;
import io.camunda.process.test.impl.client.ZeebeClientNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

public class ProcessInstanceAssertj extends AbstractAssert<ProcessInstanceAssertj, Long>
    implements ProcessInstanceAssert {

  private final ObjectMapper variableMapper = new ObjectMapper();

  private final CamundaDataSource dataSource;

  public ProcessInstanceAssertj(final CamundaDataSource dataSource, final long processInstanceKey) {
    super(processInstanceKey, ProcessInstanceAssertj.class);
    this.dataSource = dataSource;
  }

  @Override
  public ProcessInstanceAssert isActive() {
    hasProcessInstanceInState(ProcessInstanceState.ACTIVE, Objects::nonNull);
    return this;
  }

  @Override
  public ProcessInstanceAssert isCompleted() {
    hasProcessInstanceInState(ProcessInstanceState.COMPLETED, ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert isTerminated() {
    hasProcessInstanceInState(ProcessInstanceState.TERMINATED, ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasActiveElements(final String... elementNames) {
    hasElementsInState(elementNames, FlowNodeInstanceState.ACTIVE, Objects::nonNull);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasCompletedElements(final String... elementNames) {
    hasElementsInState(
        elementNames, FlowNodeInstanceState.COMPLETED, ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasTerminatedElements(final String... elementNames) {
    hasElementsInState(
        elementNames, FlowNodeInstanceState.TERMINATED, ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasVariableNames(final String... variableNames) {

    final AtomicReference<Map<String, String>> reference =
        new AtomicReference<>(Collections.emptyMap());

    try {
      Awaitility.await()
          .ignoreException(ZeebeClientNotFoundException.class)
          .untilAsserted(
              () -> {
                final Map<String, String> variables = getProcessInstanceVariables();
                reference.set(variables);

                assertThat(variables).containsKeys(variableNames);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final Map<String, String> actualVariables = reference.get();

      final List<String> missingVariableNames =
          Arrays.stream(variableNames)
              .filter(variableName -> !actualVariables.containsKey(variableName))
              .collect(Collectors.toList());

      final String failureMessage =
          String.format(
              "%s should have the variables %s but %s don't exist. All process instance variables:\n%s",
              formatProcessInstance(),
              formatNames(variableNames),
              formatNames(missingVariableNames),
              formatVariables(actualVariables));
      fail(failureMessage);
    }

    return this;
  }

  @Override
  public ProcessInstanceAssert hasVariable(final String variableName, final Object variableValue) {

    final JsonNode expectedValue = toJson(variableValue);

    final AtomicReference<Map<String, String>> reference =
        new AtomicReference<>(Collections.emptyMap());

    try {
      Awaitility.await()
          .ignoreException(ZeebeClientNotFoundException.class)
          .untilAsserted(
              () -> {
                final Map<String, String> variables = getProcessInstanceVariables();
                reference.set(variables);

                assertThat(variables).containsKey(variableName);

                final JsonNode actualValue = readJson(variables.get(variableName));
                assertThat(actualValue).isEqualTo(expectedValue);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final Map<String, String> actualVariables = reference.get();

      final String failureReason =
          Optional.ofNullable(actualVariables.get(variableName))
              .map(value -> String.format("was '%s'", value))
              .orElse("the variable doesn't exist");

      final String failureMessage =
          String.format(
              "%s should have a variable '%s' with value '%s' but %s. All process instance variables:\n%s",
              formatProcessInstance(),
              variableName,
              expectedValue,
              failureReason,
              formatVariables(actualVariables));
      fail(failureMessage);
    }

    return this;
  }

  private Map<String, String> getProcessInstanceVariables() throws IOException {
    return dataSource.getVariablesByProcessInstanceKey(actual).stream()
        .collect(Collectors.toMap(VariableDto::getName, VariableDto::getValue));
  }

  private static String formatVariables(final Map<String, String> variables) {
    return variables.entrySet().stream()
        .map(variable -> String.format("\t- '%s': %s", variable.getKey(), variable.getValue()))
        .collect(Collectors.joining("\n"));
  }

  private JsonNode readJson(final String value) {
    try {
      return variableMapper.readValue(value, JsonNode.class);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(String.format("Failed to read JSON: '%s'", value), e);
    }
  }

  private JsonNode toJson(final Object value) {
    try {
      return variableMapper.convertValue(value, JsonNode.class);
    } catch (final IllegalArgumentException e) {
      throw new RuntimeException(
          String.format("Failed to transform value to JSON: '%s'", value), e);
    }
  }

  private void hasProcessInstanceInState(
      final ProcessInstanceState expectedState, final Predicate<ProcessInstanceDto> waitCondition) {

    final AtomicReference<ProcessInstanceDto> reference = new AtomicReference<>();

    try {
      Awaitility.await()
          .ignoreException(ZeebeClientNotFoundException.class)
          .failFast(() -> waitCondition.test(reference.get()))
          .untilAsserted(
              () -> {
                final ProcessInstanceDto processInstance = dataSource.getProcessInstance(actual);
                reference.set(processInstance);

                assertThat(processInstance.getState()).isEqualTo(expectedState);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final String actualState =
          Optional.ofNullable(reference.get())
              .map(ProcessInstanceDto::getState)
              .map(ProcessInstanceAssertj::formatState)
              .orElse("not activated");

      final String failureMessage =
          String.format(
              "%s should be %s but was %s.",
              formatProcessInstance(), formatState(expectedState), actualState);
      fail(failureMessage);
    }
  }

  private void hasElementsInState(
      final String[] elementNames,
      final FlowNodeInstanceState expectedState,
      final Predicate<FlowNodeInstanceDto> waitCondition) {

    final AtomicReference<List<FlowNodeInstanceDto>> reference =
        new AtomicReference<>(Collections.emptyList());

    try {
      Awaitility.await()
          .ignoreException(ZeebeClientNotFoundException.class)
          .failFast(
              () ->
                  reference.get().stream()
                      .filter(waitCondition)
                      .map(FlowNodeInstanceDto::getFlowNodeName)
                      .collect(Collectors.toSet())
                      .containsAll(Arrays.asList(elementNames)))
          .untilAsserted(
              () -> {
                final List<FlowNodeInstanceDto> flowNodeInstances =
                    dataSource.getFlowNodeInstancesByProcessInstanceKey(actual);
                reference.set(flowNodeInstances);

                assertThat(flowNodeInstances)
                    .filteredOn(FlowNodeInstanceDto::getState, expectedState)
                    .extracting(FlowNodeInstanceDto::getFlowNodeName)
                    .contains(elementNames);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final Map<String, FlowNodeInstanceState> elementStateByName =
          reference.get().stream()
              .collect(
                  Collectors.toMap(
                      FlowNodeInstanceDto::getFlowNodeName, FlowNodeInstanceDto::getState));

      final String elementsNotInState =
          Arrays.stream(elementNames)
              .filter(elementName -> !expectedState.equals(elementStateByName.get(elementName)))
              .map(
                  elementName ->
                      String.format(
                          "\t- '%s': %s",
                          elementName, formatState(elementStateByName.get(elementName))))
              .collect(Collectors.joining("\n"));

      final String failureMessage =
          String.format(
              "%s should have %s elements %s but the following elements were not %s:\n%s",
              formatProcessInstance(),
              formatState(expectedState),
              formatNames(elementNames),
              formatState(expectedState),
              elementsNotInState);
      fail(failureMessage);
    }
  }

  private static boolean isEnded(final ProcessInstanceDto processInstance) {
    return processInstance != null && processInstance.getEndDate() != null;
  }

  private static boolean isEnded(final FlowNodeInstanceDto flowNodeInstance) {
    return flowNodeInstance.getEndDate() != null;
  }

  private String formatProcessInstance() {
    return String.format("Process instance [key: %s]", actual);
  }

  private static String formatNames(final String[] elementNames) {
    return formatNames(Arrays.asList(elementNames));
  }

  private static String formatNames(final List<String> elementNames) {
    return elementNames.stream()
        .map(elementName -> String.format("'%s'", elementName))
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private static String formatState(final ProcessInstanceState state) {
    if (state == null) {
      return "not activated";
    } else {
      return state.name().toLowerCase();
    }
  }

  private static String formatState(final FlowNodeInstanceState state) {
    if (state == null) {
      return "not activated";
    } else {
      return state.name().toLowerCase();
    }
  }
}
