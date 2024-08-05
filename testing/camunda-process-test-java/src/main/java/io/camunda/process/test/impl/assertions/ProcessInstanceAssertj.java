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

import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
import io.camunda.process.test.impl.client.FlowNodeInstanceState;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.ProcessInstanceState;
import io.camunda.process.test.impl.client.ZeebeClientNotFoundException;
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
              formatElementNames(elementNames),
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

  private static String formatElementNames(final String[] elementNames) {
    return Arrays.stream(elementNames)
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
