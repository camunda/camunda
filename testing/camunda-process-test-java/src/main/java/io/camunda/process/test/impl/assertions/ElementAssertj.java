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
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.process.test.api.assertions.ElementSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public class ElementAssertj extends AbstractAssert<ElementAssertj, String> {

  private final CamundaDataSource dataSource;
  private final Function<String, ElementSelector> elementSelector;

  protected ElementAssertj(
      final CamundaDataSource dataSource,
      final String failureMessagePrefix,
      final Function<String, ElementSelector> elementSelector) {
    super(failureMessagePrefix, ElementAssertj.class);
    this.dataSource = dataSource;
    this.elementSelector = elementSelector;
  }

  public void hasActiveElements(final long processInstanceKey, final String... elementIds) {
    hasElementsInState(
        processInstanceKey, toElementSelectors(elementIds), FlowNodeInstanceState.ACTIVE);
  }

  public void hasActiveElements(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasElementsInState(
        processInstanceKey, Arrays.asList(elementSelectors), FlowNodeInstanceState.ACTIVE);
  }

  public void hasCompletedElements(final long processInstanceKey, final String... elementIds) {
    hasElementsInState(
        processInstanceKey, toElementSelectors(elementIds), FlowNodeInstanceState.COMPLETED);
  }

  public void hasCompletedElements(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasElementsInState(
        processInstanceKey, Arrays.asList(elementSelectors), FlowNodeInstanceState.COMPLETED);
  }

  public void hasTerminatedElements(final long processInstanceKey, final String... elementIds) {
    hasElementsInState(
        processInstanceKey, toElementSelectors(elementIds), FlowNodeInstanceState.TERMINATED);
  }

  public void hasTerminatedElements(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasElementsInState(
        processInstanceKey, Arrays.asList(elementSelectors), FlowNodeInstanceState.TERMINATED);
  }

  public void hasActiveElement(
      final long processInstanceKey, final String elementId, final int expectedTimes) {
    hasElementInState(
        processInstanceKey,
        elementSelector.apply(elementId),
        FlowNodeInstanceState.ACTIVE,
        expectedTimes);
  }

  public void hasActiveElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, FlowNodeInstanceState.ACTIVE, expectedTimes);
  }

  public void hasCompletedElement(
      final long processInstanceKey, final String elementId, final int expectedTimes) {
    hasElementInState(
        processInstanceKey,
        elementSelector.apply(elementId),
        FlowNodeInstanceState.COMPLETED,
        expectedTimes);
  }

  public void hasCompletedElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, FlowNodeInstanceState.COMPLETED, expectedTimes);
  }

  public void hasTerminatedElement(
      final long processInstanceKey, final String elementId, final int expectedTimes) {
    hasElementInState(
        processInstanceKey,
        elementSelector.apply(elementId),
        FlowNodeInstanceState.TERMINATED,
        expectedTimes);
  }

  public void hasTerminatedElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, FlowNodeInstanceState.TERMINATED, expectedTimes);
  }

  public void hasNotActivatedElements(final long processInstanceKey, final String... elementIds) {
    hasNotActivatedElements(processInstanceKey, toElementSelectors(elementIds));
  }

  public void hasNotActivatedElements(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasNotActivatedElements(processInstanceKey, Arrays.asList(elementSelectors));
  }

  public void hasNoActiveElements(final long processInstanceKey, final String... elementIds) {
    hasNoActiveElements(processInstanceKey, toElementSelectors(elementIds));
  }

  public void hasNoActiveElements(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasNoActiveElements(processInstanceKey, Arrays.asList(elementSelectors));
  }

  public void hasActiveElementsExactly(final long processInstanceKey, final String... elementIds) {
    hasActiveElementsExactly(processInstanceKey, toElementSelectors(elementIds));
  }

  public void hasActiveElementsExactly(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasActiveElementsExactly(processInstanceKey, Arrays.asList(elementSelectors));
  }

  private void hasElementsInState(
      final long processInstanceKey,
      final List<ElementSelector> elementSelectors,
      final FlowNodeInstanceState expectedState) {

    awaitFlowNodeInstanceAssertion(
        flowNodeInstanceFilter(processInstanceKey, elementSelectors),
        flowNodeInstances -> {
          final List<FlowNodeInstance> flowNodeInstancesInState =
              getInstancesInState(flowNodeInstances, expectedState);

          final List<ElementSelector> selectorsNotMatched =
              getSelectorsWithoutInstances(elementSelectors, flowNodeInstancesInState);

          assertThat(selectorsNotMatched)
              .withFailMessage(
                  () ->
                      String.format(
                          "%s should have %s elements %s but the following elements were not %s:\n%s",
                          actual,
                          formatState(expectedState),
                          formatElementSelectors(elementSelectors),
                          formatState(expectedState),
                          formatFlowNodeInstanceStates(selectorsNotMatched, flowNodeInstances)))
              .isEmpty();
        });
  }

  private void hasElementInState(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final FlowNodeInstanceState expectedState,
      final int expectedTimes) {

    if (expectedTimes < 1) {
      throw new IllegalArgumentException("The amount must be greater than zero.");
    }

    awaitFlowNodeInstanceAssertion(
        flowNodeInstanceFilter(processInstanceKey, Collections.singletonList(elementSelector)),
        flowNodeInstances -> {
          final List<FlowNodeInstance> elementInstances =
              flowNodeInstances.stream().filter(elementSelector::test).collect(Collectors.toList());

          final long actualTimes = getInstancesInState(elementInstances, expectedState).size();

          assertThat(actualTimes)
              .withFailMessage(
                  () ->
                      String.format(
                          "%s should have %s element '%s' %d times but was %d. Element instances:\n%s",
                          actual,
                          formatState(expectedState),
                          elementSelector.describe(),
                          expectedTimes,
                          actualTimes,
                          elementInstances.isEmpty()
                              ? "<None>"
                              : formatFlowNodeInstanceStates(
                                  elementInstances, elementInstance -> elementSelector.describe())))
              .isEqualTo(expectedTimes);
        });
  }

  private void hasNotActivatedElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    final List<FlowNodeInstance> flowNodeInstances =
        dataSource.findFlowNodeInstances(
            flowNodeInstanceFilter(processInstanceKey, elementSelectors));

    final List<ElementSelector> activatedElements =
        getSelectorsWithInstances(elementSelectors, flowNodeInstances);

    assertThat(activatedElements)
        .withFailMessage(
            () ->
                String.format(
                    "%s should have not activated elements %s but the following elements were activated:\n%s",
                    actual,
                    formatElementSelectors(elementSelectors),
                    formatFlowNodeInstanceStates(activatedElements, flowNodeInstances)))
        .isEmpty();
  }

  private void hasNoActiveElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    final Consumer<FlownodeInstanceFilter> flowNodeInstanceFilter =
        flowNodeInstanceFilter(processInstanceKey, elementSelectors)
            .andThen(filter -> filter.state(FlowNodeInstanceState.ACTIVE));

    awaitFlowNodeInstanceAssertion(
        flowNodeInstanceFilter,
        flowNodeInstances -> {
          final List<ElementSelector> selectorsNotMatched =
              getSelectorsWithInstances(elementSelectors, flowNodeInstances);

          assertThat(selectorsNotMatched)
              .withFailMessage(
                  () ->
                      String.format(
                          "%s should have no active elements %s but the following elements were active:\n%s",
                          actual,
                          formatElementSelectors(elementSelectors),
                          formatFlowNodeInstanceStates(selectorsNotMatched, flowNodeInstances)))
              .isEmpty();
        });
  }

  private void hasActiveElementsExactly(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    awaitFlowNodeInstanceAssertion(
        processInstanceFilter(processInstanceKey),
        flowNodeInstances -> {
          final List<FlowNodeInstance> activeFlowNodeInstances =
              getInstancesInState(flowNodeInstances, FlowNodeInstanceState.ACTIVE);

          final List<ElementSelector> selectorsNotMatched =
              getSelectorsWithoutInstances(elementSelectors, activeFlowNodeInstances);

          final List<FlowNodeInstance> otherActiveElements =
              activeFlowNodeInstances.stream()
                  .filter(
                      flowNodeInstance ->
                          elementSelectors.stream()
                              .noneMatch(selector -> selector.test(flowNodeInstance)))
                  .collect(Collectors.toList());

          final Supplier<String> failureMessage =
              () -> {
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
                return String.join("\n\n", failureMessages);
              };

          assertThat(selectorsNotMatched).withFailMessage(failureMessage).isEmpty();
          assertThat(otherActiveElements).withFailMessage(failureMessage).isEmpty();
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
              () -> {
                final List<FlowNodeInstance> flowNodeInstances =
                    dataSource.findFlowNodeInstances(filter);

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
      final List<FlowNodeInstance> flowNodeInstances, final FlowNodeInstanceState state) {
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
              final FlowNodeInstanceState flowNodeInstanceState =
                  getFlowNodeInstanceStateForSelector(flowNodeInstances, elementSelector);

              return String.format(
                  "\t- '%s': %s", elementSelector.describe(), formatState(flowNodeInstanceState));
            })
        .collect(Collectors.joining("\n"));
  }

  private static FlowNodeInstanceState getFlowNodeInstanceStateForSelector(
      final List<FlowNodeInstance> flowNodeInstances, final ElementSelector elementSelector) {

    return flowNodeInstances.stream()
        .filter(elementSelector::test)
        .findFirst()
        .map(FlowNodeInstance::getState)
        .orElse(FlowNodeInstanceState.UNKNOWN_ENUM_VALUE);
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

  private List<ElementSelector> toElementSelectors(final String[] elementIds) {
    return Arrays.stream(elementIds).map(elementSelector).collect(Collectors.toList());
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
