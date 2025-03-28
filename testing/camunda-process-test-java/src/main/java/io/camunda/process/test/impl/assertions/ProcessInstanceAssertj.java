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

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

public class ProcessInstanceAssertj
    extends AbstractAssert<ProcessInstanceAssertj, ProcessInstanceSelector>
    implements ProcessInstanceAssert {

  private final CamundaDataSource dataSource;

  private final ElementAssertj elementAssertj;
  private final VariableAssertj variableAssertj;
  private final String failureMessagePrefix;
  private final Function<String, ElementSelector> elementSelector;

  private final AtomicReference<ProcessInstance> actualProcessInstance = new AtomicReference<>();

  public ProcessInstanceAssertj(
      final CamundaDataSource dataSource,
      final long processInstanceKey,
      final Function<String, ElementSelector> elementSelector) {
    this(dataSource, ProcessInstanceSelectors.byKey(processInstanceKey), elementSelector);
  }

  public ProcessInstanceAssertj(
      final CamundaDataSource dataSource,
      final ProcessInstanceSelector processInstanceSelector,
      final Function<String, ElementSelector> elementSelector) {
    super(processInstanceSelector, ProcessInstanceAssertj.class);
    this.dataSource = dataSource;
    failureMessagePrefix =
        String.format("Process instance [%s]", processInstanceSelector.describe());
    this.elementSelector = elementSelector;
    elementAssertj = new ElementAssertj(dataSource, failureMessagePrefix);
    variableAssertj = new VariableAssertj(dataSource, failureMessagePrefix);
  }

  @Override
  public ProcessInstanceAssert isActive() {
    hasProcessInstanceInState("active", ProcessInstanceState.ACTIVE::equals, Objects::nonNull);
    return this;
  }

  @Override
  public ProcessInstanceAssert isCompleted() {
    hasProcessInstanceInState(
        "completed", ProcessInstanceState.COMPLETED::equals, ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert isTerminated() {
    hasProcessInstanceInState(
        "terminated", ProcessInstanceState.TERMINATED::equals, ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert isCreated() {
    hasProcessInstanceInState(
        "created",
        state ->
            state == ProcessInstanceState.ACTIVE
                || state == ProcessInstanceState.COMPLETED
                || state == ProcessInstanceState.TERMINATED,
        Objects::nonNull);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasActiveElements(final String... elementIds) {
    elementAssertj.hasActiveElements(getProcessInstanceKey(), toElementSelectors(elementIds));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasActiveElements(final ElementSelector... elementSelectors) {
    elementAssertj.hasActiveElements(getProcessInstanceKey(), Arrays.asList(elementSelectors));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasCompletedElements(final String... elementIds) {
    elementAssertj.hasCompletedElements(getProcessInstanceKey(), toElementSelectors(elementIds));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasCompletedElements(final ElementSelector... elementSelectors) {
    elementAssertj.hasCompletedElements(getProcessInstanceKey(), Arrays.asList(elementSelectors));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasTerminatedElements(final String... elementIds) {
    elementAssertj.hasTerminatedElements(getProcessInstanceKey(), toElementSelectors(elementIds));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasTerminatedElements(final ElementSelector... elementSelectors) {
    elementAssertj.hasTerminatedElements(getProcessInstanceKey(), Arrays.asList(elementSelectors));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasActiveElement(final String elementId, final int times) {
    elementAssertj.hasActiveElement(
        getProcessInstanceKey(), elementSelector.apply(elementId), times);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasActiveElement(
      final ElementSelector elementSelector, final int times) {
    elementAssertj.hasActiveElement(getProcessInstanceKey(), elementSelector, times);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasCompletedElement(final String elementId, final int times) {
    elementAssertj.hasCompletedElement(
        getProcessInstanceKey(), elementSelector.apply(elementId), times);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasCompletedElement(
      final ElementSelector elementSelector, final int times) {
    elementAssertj.hasCompletedElement(getProcessInstanceKey(), elementSelector, times);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasTerminatedElement(final String elementId, final int times) {
    elementAssertj.hasTerminatedElement(
        getProcessInstanceKey(), elementSelector.apply(elementId), times);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasTerminatedElement(
      final ElementSelector elementSelector, final int times) {
    elementAssertj.hasTerminatedElement(getProcessInstanceKey(), elementSelector, times);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasNotActivatedElements(final String... elementIds) {
    elementAssertj.hasNotActivatedElements(getProcessInstanceKey(), toElementSelectors(elementIds));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasNotActivatedElements(final ElementSelector... elementSelectors) {
    elementAssertj.hasNotActivatedElements(
        getProcessInstanceKey(), Arrays.asList(elementSelectors));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasNoActiveElements(final String... elementIds) {
    elementAssertj.hasNoActiveElements(getProcessInstanceKey(), toElementSelectors(elementIds));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasNoActiveElements(final ElementSelector... elementSelectors) {
    elementAssertj.hasNoActiveElements(getProcessInstanceKey(), Arrays.asList(elementSelectors));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasActiveElementsExactly(final String... elementIds) {
    elementAssertj.hasActiveElementsExactly(
        getProcessInstanceKey(), toElementSelectors(elementIds));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasActiveElementsExactly(final ElementSelector... elementSelectors) {
    elementAssertj.hasActiveElementsExactly(
        getProcessInstanceKey(), Arrays.asList(elementSelectors));
    return this;
  }

  @Override
  public ProcessInstanceAssert hasVariableNames(final String... variableNames) {
    variableAssertj.hasVariableNames(getProcessInstanceKey(), variableNames);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasVariable(final String variableName, final Object variableValue) {
    variableAssertj.hasVariable(getProcessInstanceKey(), variableName, variableValue);
    return this;
  }

  @Override
  public ProcessInstanceAssert hasVariables(final Map<String, Object> variables) {
    variableAssertj.hasVariables(getProcessInstanceKey(), variables);
    return this;
  }

  private void hasProcessInstanceInState(
      final String expectedState,
      final Predicate<ProcessInstanceState> expectedStateMatcher,
      final Predicate<ProcessInstance> waitCondition) {
    // reset cached process instance
    actualProcessInstance.set(null);

    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .failFast(() -> waitCondition.test(actualProcessInstance.get()))
          .untilAsserted(
              () -> {
                final Optional<ProcessInstance> processInstance = findProcessInstance();
                processInstance.ifPresent(actualProcessInstance::set);

                assertThat(processInstance).isPresent();
                assertThat(processInstance.get().getState()).matches(expectedStateMatcher);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final String actualState =
          Optional.ofNullable(actualProcessInstance.get())
              .map(ProcessInstance::getState)
              .map(ProcessInstanceAssertj::formatState)
              .orElse("not created");

      final String failureMessage =
          String.format(
              "%s should be %s but was %s.", failureMessagePrefix, expectedState, actualState);
      fail(failureMessage);
    }
  }

  private Optional<ProcessInstance> findProcessInstance() {
    return dataSource.findProcessInstances(actual::applyFilter).stream()
        .filter(actual::test)
        .findFirst();
  }

  private void awaitProcessInstance() {
    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> {
                final Optional<ProcessInstance> processInstance = findProcessInstance();
                processInstance.ifPresent(actualProcessInstance::set);

                assertThat(processInstance).isPresent();
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {
      final String failureMessage =
          String.format("No process instance [%s] found.", actual.describe());
      fail(failureMessage);
    }
  }

  private long getProcessInstanceKey() {
    if (actualProcessInstance.get() == null) {
      awaitProcessInstance();
    }
    return actualProcessInstance.get().getProcessInstanceKey();
  }

  private static boolean isEnded(final ProcessInstance processInstance) {
    return processInstance != null && processInstance.getEndDate() != null;
  }

  private static String formatState(final ProcessInstanceState state) {
    if (state == null || state == ProcessInstanceState.UNKNOWN_ENUM_VALUE) {
      return "not created";
    } else {
      return state.name().toLowerCase();
    }
  }

  private List<ElementSelector> toElementSelectors(final String[] elementIds) {
    return Arrays.stream(elementIds).map(elementSelector).collect(Collectors.toList());
  }
}
