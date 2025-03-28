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
import io.camunda.client.api.search.enums.FlowNodeInstanceFilterState;
import io.camunda.client.api.search.enums.FlowNodeInstanceResultState;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.process.test.api.assertions.ElementSelector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public class ElementAssertj extends AbstractAssert<ElementAssertj, String> {

  private final CamundaDataSource dataSource;

  protected ElementAssertj(final CamundaDataSource dataSource, final String failureMessagePrefix) {
    super(failureMessagePrefix, ElementAssertj.class);
    this.dataSource = dataSource;
  }

  public void hasActiveElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {
    hasElementsInState(processInstanceKey, elementSelectors, FlowNodeInstanceResultState.ACTIVE);
  }

  public void hasCompletedElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {
    hasElementsInState(processInstanceKey, elementSelectors, FlowNodeInstanceResultState.COMPLETED);
  }

  public void hasTerminatedElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {
    hasElementsInState(
        processInstanceKey, elementSelectors, FlowNodeInstanceResultState.TERMINATED);
  }

  private void hasElementsInState(
      final long processInstanceKey,
      final List<ElementSelector> elementSelectors,
      final FlowNodeInstanceResultState expectedState) {

    awaitFlowNodeInstanceAssertion(
        flowNodeInstanceFilter(processInstanceKey, elementSelectors),
        flowNodeInstances -> {
          final List<FlowNodeInstance> flowNodeInstancesInState =
              getInstancesInState(flowNodeInstances, expectedState);

          final List<ElementSelector> selectorsNotMatched =
              getSelectorsWithoutInstances(elementSelectors, flowNodeInstancesInState);

          assertThat(selectorsNotMatched)
              .withFailMessage(
                  "%s should have %s elements %s but the following elements were not %s:\n%s",
                  actual,
                  formatState(expectedState),
                  formatElementSelectors(elementSelectors),
                  formatState(expectedState),
                  formatFlowNodeInstanceStates(selectorsNotMatched, flowNodeInstances))
              .isEmpty();
        });
  }

  public void hasActiveElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, FlowNodeInstanceResultState.ACTIVE, expectedTimes);
  }

  public void hasCompletedElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, FlowNodeInstanceResultState.COMPLETED, expectedTimes);
  }

  public void hasTerminatedElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, FlowNodeInstanceResultState.TERMINATED, expectedTimes);
  }

  private void hasElementInState(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final FlowNodeInstanceResultState expectedState,
      final int expectedTimes) {

    if (expectedTimes < 0) {
      throw new IllegalArgumentException("The amount must be greater than or equal to zero.");
    }

    awaitFlowNodeInstanceAssertion(
        flowNodeInstanceFilter(processInstanceKey, Collections.singletonList(elementSelector)),
        flowNodeInstances -> {
          final List<FlowNodeInstance> elementInstances =
              flowNodeInstances.stream().filter(elementSelector::test).collect(Collectors.toList());

          final long actualTimes = getInstancesInState(elementInstances, expectedState).size();

          assertThat(actualTimes)
              .withFailMessage(
                  "%s should have %s element '%s' %d times but was %d. Element instances:\n%s",
                  actual,
                  formatState(expectedState),
                  elementSelector.describe(),
                  expectedTimes,
                  actualTimes,
                  elementInstances.isEmpty()
                      ? "<None>"
                      : formatFlowNodeInstanceStates(
                          elementInstances, elementInstance -> elementSelector.describe()))
              .isEqualTo(expectedTimes);
        });
  }

  public void hasNotActivatedElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    final List<FlowNodeInstance> flowNodeInstances =
        dataSource.findFlowNodeInstances(
            flowNodeInstanceFilter(processInstanceKey, elementSelectors));

    final List<ElementSelector> activatedElements =
        getSelectorsWithInstances(elementSelectors, flowNodeInstances);

    assertThat(activatedElements)
        .withFailMessage(
            "%s should have not activated elements %s but the following elements were activated:\n%s",
            actual,
            formatElementSelectors(elementSelectors),
            formatFlowNodeInstanceStates(activatedElements, flowNodeInstances))
        .isEmpty();
  }

  public void hasNoActiveElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    final Consumer<FlownodeInstanceFilter> flowNodeInstanceFilter =
        flowNodeInstanceFilter(processInstanceKey, elementSelectors)
            .andThen(filter -> filter.state(FlowNodeInstanceFilterState.ACTIVE));

    awaitFlowNodeInstanceAssertion(
        flowNodeInstanceFilter,
        flowNodeInstances -> {
          final List<ElementSelector> selectorsWithActiveElements =
              getSelectorsWithInstances(elementSelectors, flowNodeInstances);

          assertThat(selectorsWithActiveElements)
              .withFailMessage(
                  "%s should have no active elements %s but the following elements were active:\n%s",
                  actual,
                  formatElementSelectors(elementSelectors),
                  formatFlowNodeInstanceStates(selectorsWithActiveElements, flowNodeInstances))
              .isEmpty();
        });
  }

  public void hasActiveElementsExactly(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    awaitFlowNodeInstanceAssertion(
        processInstanceFilter(processInstanceKey),
        flowNodeInstances -> {
          final List<FlowNodeInstance> activeFlowNodeInstances =
              getInstancesInState(flowNodeInstances, FlowNodeInstanceResultState.ACTIVE);

          final List<ElementSelector> selectorsNotMatched =
              getSelectorsWithoutInstances(elementSelectors, activeFlowNodeInstances);

          final List<FlowNodeInstance> otherActiveElements =
              activeFlowNodeInstances.stream()
                  .filter(
                      flowNodeInstance ->
                          elementSelectors.stream()
                              .noneMatch(selector -> selector.test(flowNodeInstance)))
                  .collect(Collectors.toList());

          final List<String> failureMessages = new ArrayList<>();
          if (!selectorsNotMatched.isEmpty()) {
            failureMessages.add(
                String.format(
                    "%s should have active elements %s but the following elements were not active:\n%s",
                    actual,
                    formatElementSelectors(elementSelectors),
                    formatFlowNodeInstanceStates(selectorsNotMatched, flowNodeInstances)));
          }
          if (!otherActiveElements.isEmpty()) {
            failureMessages.add(
                String.format(
                    "%s should have no active elements except %s but the following elements were active:\n%s",
                    actual,
                    formatElementSelectors(elementSelectors),
                    formatFlowNodeInstanceStates(
                        otherActiveElements, FlowNodeInstance::getFlowNodeId)));
          }
          final String combinedFailureMessage = String.join("\n\n", failureMessages);

          assertThat(selectorsNotMatched).withFailMessage(combinedFailureMessage).isEmpty();
          assertThat(otherActiveElements).withFailMessage(combinedFailureMessage).isEmpty();
        });
  }

  private void awaitFlowNodeInstanceAssertion(
      final Consumer<FlownodeInstanceFilter> filter,
      final Consumer<List<FlowNodeInstance>> assertion) {
    // If await() times out, the exception doesn't contain the assertion error. Use a reference to
    // store the error's failure message.
    final AtomicReference<String> failureMessage = new AtomicReference<>("?");
    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> dataSource.findFlowNodeInstances(filter),
              flowNodeInstances -> {
                try {
                  assertion.accept(flowNodeInstances);
                } catch (final AssertionError e) {
                  failureMessage.set(e.getMessage());
                  throw e;
                }
              });

    } catch (final ConditionTimeoutException ignore) {
      fail(failureMessage.get());
    }
  }

  private static List<FlowNodeInstance> getInstancesInState(
      final List<FlowNodeInstance> flowNodeInstances, final FlowNodeInstanceResultState state) {
    return flowNodeInstances.stream()
        .filter(flowNodeInstance -> flowNodeInstance.getState().equals(state))
        .collect(Collectors.toList());
  }

  private static List<ElementSelector> getSelectorsWithoutInstances(
      final List<ElementSelector> elementSelectors,
      final List<FlowNodeInstance> flowNodeInstances) {
    return elementSelectors.stream()
        .filter(elementSelector -> flowNodeInstances.stream().noneMatch(elementSelector::test))
        .collect(Collectors.toList());
  }

  private static List<ElementSelector> getSelectorsWithInstances(
      final List<ElementSelector> elementSelectors,
      final List<FlowNodeInstance> flowNodeInstances) {
    return elementSelectors.stream()
        .filter(elementSelector -> flowNodeInstances.stream().anyMatch(elementSelector::test))
        .collect(Collectors.toList());
  }

  private static String formatFlowNodeInstanceStates(
      final List<ElementSelector> elementSelectors,
      final List<FlowNodeInstance> flowNodeInstances) {

    return elementSelectors.stream()
        .map(
            elementSelector -> {
              final FlowNodeInstanceResultState flowNodeInstanceState =
                  getFlowNodeInstanceStateForSelector(flowNodeInstances, elementSelector);

              return String.format(
                  "\t- '%s': %s", elementSelector.describe(), formatState(flowNodeInstanceState));
            })
        .collect(Collectors.joining("\n"));
  }

  private static FlowNodeInstanceResultState getFlowNodeInstanceStateForSelector(
      final List<FlowNodeInstance> flowNodeInstances, final ElementSelector elementSelector) {

    return flowNodeInstances.stream()
        .filter(elementSelector::test)
        .findFirst()
        .map(FlowNodeInstance::getState)
        .orElse(FlowNodeInstanceResultState.UNKNOWN_ENUM_VALUE);
  }

  private static String formatFlowNodeInstanceStates(
      final List<FlowNodeInstance> flowNodeInstances,
      final Function<FlowNodeInstance, String> flowNodeDescriptor) {

    return flowNodeInstances.stream()
        .map(
            flowNodeInstance ->
                String.format(
                    "\t- '%s': %s",
                    flowNodeDescriptor.apply(flowNodeInstance),
                    formatState(flowNodeInstance.getState())))
        .collect(Collectors.joining("\n"));
  }

  private static String formatState(final FlowNodeInstanceResultState state) {
    if (state == null || state == FlowNodeInstanceResultState.UNKNOWN_ENUM_VALUE) {
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

  private static Consumer<FlownodeInstanceFilter> processInstanceFilter(
      final long processInstanceKey) {
    return filter -> filter.processInstanceKey(processInstanceKey);
  }

  private static Consumer<FlownodeInstanceFilter> flowNodeInstanceFilter(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {
    return elementSelectors.size() == 1
        ? processInstanceFilter(processInstanceKey).andThen(elementSelectors.get(0)::applyFilter)
        : processInstanceFilter(processInstanceKey);
  }
}
