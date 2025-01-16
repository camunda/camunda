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

import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.impl.client.CamundaClientNotFoundException;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.ProcessInstanceState;
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

  private final CamundaDataSource dataSource;
  private final VariableAssertj variableAssertj;

  public ProcessInstanceAssertj(final CamundaDataSource dataSource, final long processInstanceKey) {
    super(processInstanceKey, ProcessInstanceAssertj.class);
    this.dataSource = dataSource;
    variableAssertj = new VariableAssertj(dataSource, processInstanceKey);
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
  public ProcessInstanceAssert hasActiveElements(final String... elementIds) {
    hasElementsInState(
        asElementIdSelectors(elementIds), FlowNodeInstanceState.ACTIVE, Objects::nonNull);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasActiveElements(final ElementSelector... elementSelectors) {
    hasElementsInState(
        Arrays.asList(elementSelectors), FlowNodeInstanceState.ACTIVE, Objects::nonNull);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasCompletedElements(final String... elementIds) {
    hasElementsInState(
        asElementIdSelectors(elementIds),
        FlowNodeInstanceState.COMPLETED,
        ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasCompletedElements(final ElementSelector... elementSelectors) {
    hasElementsInState(
        Arrays.asList(elementSelectors),
        FlowNodeInstanceState.COMPLETED,
        ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasTerminatedElements(final String... elementIds) {
    hasElementsInState(
        asElementIdSelectors(elementIds),
        FlowNodeInstanceState.TERMINATED,
        ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasTerminatedElements(final ElementSelector... elementSelectors) {
    hasElementsInState(
        Arrays.asList(elementSelectors),
        FlowNodeInstanceState.TERMINATED,
        ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasVariableNames(final String... variableNames) {
    variableAssertj.hasVariableNames(variableNames);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasVariable(final String variableName, final Object variableValue) {
    variableAssertj.hasVariable(variableName, variableValue);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasVariables(final Map<String, Object> variables) {
    variableAssertj.hasVariables(variables);
    return this;
  }

  private static List<ElementSelector> asElementIdSelectors(final String[] elementIds) {
    return Arrays.stream(elementIds).map(ElementSelectors::byId).collect(Collectors.toList());
  }

  private void hasProcessInstanceInState(
      final ProcessInstanceState expectedState, final Predicate<ProcessInstanceDto> waitCondition) {

    final AtomicReference<ProcessInstanceDto> reference = new AtomicReference<>();

    try {
      Awaitility.await()
          .ignoreException(CamundaClientNotFoundException.class)
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
              AssertFormatUtil.formatProcessInstance(actual),
              formatState(expectedState),
              actualState);
      fail(failureMessage);
    }
  }

  private void hasElementsInState(
      final List<ElementSelector> elementSelectors,
      final FlowNodeInstanceState expectedState,
      final Predicate<FlowNodeInstance> waitCondition) {

    final AtomicReference<List<FlowNodeInstance>> reference =
        new AtomicReference<>(Collections.emptyList());

    try {
      Awaitility.await()
          .ignoreException(CamundaClientNotFoundException.class)
          .failFast(
              () -> {
                final List<FlowNodeInstance> flowNodeInstances =
                    reference.get().stream().filter(waitCondition).collect(Collectors.toList());
                return elementSelectors.stream()
                    .allMatch(
                        elementSelector ->
                            flowNodeInstances.stream().anyMatch(elementSelector::test));
              })
          .untilAsserted(
              () -> {
                final List<FlowNodeInstance> flowNodeInstances =
                    dataSource.getFlowNodeInstancesByProcessInstanceKey(actual);
                reference.set(flowNodeInstances);

                final List<FlowNodeInstance> flowNodeInstancesInState =
                    flowNodeInstances.stream()
                        .filter(
                            flowNodeInstance -> flowNodeInstance.getState().equals(expectedState))
                        .collect(Collectors.toList());

                assertThat(elementSelectors)
                    .allMatch(
                        elementSelector ->
                            flowNodeInstancesInState.stream().anyMatch(elementSelector::test));
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final List<ElementSelector> selectorsNotMatched =
          elementSelectors.stream()
              .filter(
                  elementSelector ->
                      reference.get().stream()
                          .noneMatch(
                              element ->
                                  elementSelector.test(element)
                                      && element.getState().equals(expectedState)))
              .collect(Collectors.toList());

      final String elementsNotInState =
          selectorsNotMatched.stream()
              .map(
                  elementSelector -> {
                    final FlowNodeInstanceState elementState =
                        reference.get().stream()
                            .filter(elementSelector::test)
                            .findFirst()
                            .map(FlowNodeInstance::getState)
                            .orElse(FlowNodeInstanceState.UNKNOWN_ENUM_VALUE);

                    return String.format(
                        "\t- '%s': %s", elementSelector.describe(), formatState(elementState));
                  })
              .collect(Collectors.joining("\n"));

      final String failureMessage =
          String.format(
              "%s should have %s elements %s but the following elements were not %s:\n%s",
              AssertFormatUtil.formatProcessInstance(actual),
              formatState(expectedState),
              formatElementSelectors(elementSelectors),
              formatState(expectedState),
              elementsNotInState);
      fail(failureMessage);
    }
  }

  private static boolean isEnded(final ProcessInstanceDto processInstance) {
    return processInstance != null && processInstance.getEndDate() != null;
  }

  private static boolean isEnded(final FlowNodeInstance flowNodeInstance) {
    return flowNodeInstance.getEndDate() != null;
  }

  private static String formatState(final ProcessInstanceState state) {
    if (state == null) {
      return "not activated";
    } else {
      return state.name().toLowerCase();
    }
  }

  private static String formatState(final FlowNodeInstanceState state) {
    if (state == null || state == FlowNodeInstanceState.UNKNOWN_ENUM_VALUE) {
      return "not activated";
    } else {
      return state.name().toLowerCase();
    }
  }

  private static String formatElementSelectors(final List<ElementSelector> elementSelectors) {
    final List<String> selectorList =
        elementSelectors.stream().map(ElementSelector::describe).collect(Collectors.toList());
    return AssertFormatUtil.formatNames(selectorList);
  }
}
