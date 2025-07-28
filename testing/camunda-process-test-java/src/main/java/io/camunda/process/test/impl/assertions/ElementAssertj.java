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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.assertions.ElementSelector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.AbstractAssert;

public class ElementAssertj extends AbstractAssert<ElementAssertj, String> {

  private final CamundaDataSource dataSource;
  private final CamundaAssertAwaitBehavior awaitBehavior;

  protected ElementAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final String failureMessagePrefix) {
    super(failureMessagePrefix, ElementAssertj.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
  }

  public void hasActiveElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {
    hasElementsInState(processInstanceKey, elementSelectors, ElementInstanceState.ACTIVE);
  }

  public void hasCompletedElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {
    hasElementsInState(processInstanceKey, elementSelectors, ElementInstanceState.COMPLETED);
  }

  public void hasTerminatedElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {
    hasElementsInState(processInstanceKey, elementSelectors, ElementInstanceState.TERMINATED);
  }

  private void hasElementsInState(
      final long processInstanceKey,
      final List<ElementSelector> elementSelectors,
      final ElementInstanceState expectedState) {

    awaitElementInstanceAssertion(
        elementInstanceFilter(processInstanceKey, elementSelectors),
        elementInstances -> {
          final List<ElementInstance> elementInstancesInState =
              getInstancesInState(elementInstances, expectedState);

          final List<ElementSelector> selectorsNotMatched =
              getSelectorsWithoutInstances(elementSelectors, elementInstancesInState);

          assertThat(selectorsNotMatched)
              .withFailMessage(
                  "%s should have %s elements %s but the following elements were not %s:\n%s",
                  actual,
                  formatState(expectedState),
                  formatElementSelectors(elementSelectors),
                  formatState(expectedState),
                  formatElementInstanceStates(selectorsNotMatched, elementInstances))
              .isEmpty();
        });
  }

  public void hasActiveElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, ElementInstanceState.ACTIVE, expectedTimes);
  }

  public void hasCompletedElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, ElementInstanceState.COMPLETED, expectedTimes);
  }

  public void hasTerminatedElement(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final int expectedTimes) {
    hasElementInState(
        processInstanceKey, elementSelector, ElementInstanceState.TERMINATED, expectedTimes);
  }

  private void hasElementInState(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final ElementInstanceState expectedState,
      final int expectedTimes) {

    if (expectedTimes < 0) {
      throw new IllegalArgumentException("The amount must be greater than or equal to zero.");
    }

    awaitElementInstanceAssertion(
        elementInstanceFilter(processInstanceKey, Collections.singletonList(elementSelector)),
        elementInstancesUnfiltered -> {
          final List<ElementInstance> elementInstances =
              elementInstancesUnfiltered.stream()
                  .filter(elementSelector::test)
                  .collect(Collectors.toList());

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
                      : formatElementInstanceStates(
                          elementInstances, elementInstance -> elementSelector.describe()))
              .isEqualTo(expectedTimes);
        });
  }

  public void hasNotActivatedElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    final List<ElementInstance> elementInstances =
        dataSource.findElementInstances(
            elementInstanceFilter(processInstanceKey, elementSelectors));

    final List<ElementSelector> activatedElements =
        getSelectorsWithInstances(elementSelectors, elementInstances);

    assertThat(activatedElements)
        .withFailMessage(
            "%s should have not activated elements %s but the following elements were activated:\n%s",
            actual,
            formatElementSelectors(elementSelectors),
            formatElementInstanceStates(activatedElements, elementInstances))
        .isEmpty();
  }

  public void hasNoActiveElements(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    final Consumer<ElementInstanceFilter> elementInstanceFilter =
        elementInstanceFilter(processInstanceKey, elementSelectors)
            .andThen(filter -> filter.state(ElementInstanceState.ACTIVE));

    awaitElementInstanceAssertion(
        elementInstanceFilter,
        elementInstances -> {
          final List<ElementSelector> selectorsWithActiveElements =
              getSelectorsWithInstances(elementSelectors, elementInstances);

          assertThat(selectorsWithActiveElements)
              .withFailMessage(
                  "%s should have no active elements %s but the following elements were active:\n%s",
                  actual,
                  formatElementSelectors(elementSelectors),
                  formatElementInstanceStates(selectorsWithActiveElements, elementInstances))
              .isEmpty();
        });
  }

  public void hasActiveElementsExactly(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    awaitElementInstanceAssertion(
        processInstanceFilter(processInstanceKey),
        elementInstances -> {
          final List<ElementInstance> activeElementInstances =
              getInstancesInState(elementInstances, ElementInstanceState.ACTIVE);

          final List<ElementSelector> selectorsNotMatched =
              getSelectorsWithoutInstances(elementSelectors, activeElementInstances);

          final List<ElementInstance> otherActiveElements =
              activeElementInstances.stream()
                  .filter(
                      elementInstance ->
                          elementSelectors.stream()
                              .noneMatch(selector -> selector.test(elementInstance)))
                  .collect(Collectors.toList());

          final List<String> failureMessages = new ArrayList<>();
          if (!selectorsNotMatched.isEmpty()) {
            failureMessages.add(
                String.format(
                    "%s should have active elements %s but the following elements were not active:\n%s",
                    actual,
                    formatElementSelectors(elementSelectors),
                    formatElementInstanceStates(selectorsNotMatched, elementInstances)));
          }
          if (!otherActiveElements.isEmpty()) {
            failureMessages.add(
                String.format(
                    "%s should have no active elements except %s but the following elements were active:\n%s",
                    actual,
                    formatElementSelectors(elementSelectors),
                    formatElementInstanceStates(
                        otherActiveElements, ElementInstance::getElementId)));
          }
          final String combinedFailureMessage = String.join("\n\n", failureMessages);

          assertThat(selectorsNotMatched).withFailMessage(combinedFailureMessage).isEmpty();
          assertThat(otherActiveElements).withFailMessage(combinedFailureMessage).isEmpty();
        });
  }

  public void hasCompletedElementsInOrder(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {

    awaitElementInstanceAssertion(
        processInstanceFilter(processInstanceKey),
        elementInstances -> {
          final List<ElementInstance> relevantElementInstances =
              getInstancesInState(elementInstances, ElementInstanceState.COMPLETED).stream()
                  .filter(i -> elementSelectors.stream().anyMatch(e -> e.test(i)))
                  .collect(Collectors.toList());

          // Loop over elementInstances and check if the order is correct. With each iteration,
          // remove the first element of the element instance list and call isSequenceFound() to
          // with the remaining list.
          // This ensures we pass with instances 'A, B, A' for selectors 'B, A'.
          final boolean sequenceFound =
              IntStream.rangeClosed(0, relevantElementInstances.size() - elementSelectors.size())
                  .mapToObj(
                      index ->
                          relevantElementInstances.subList(index, relevantElementInstances.size()))
                  .anyMatch(sublist -> containsSequence(sublist, elementSelectors));

          if (!sequenceFound) {
            fail(
                String.format(
                    "%s should have completed elements %s in order, but only the following elements were completed:\n%s",
                    actual,
                    formatElementSelectors(elementSelectors),
                    formatElementInstanceStates(
                        relevantElementInstances, ElementInstance::getElementId)));
          }
        });
  }

  private static boolean containsSequence(
      final List<ElementInstance> elementInstances, final List<ElementSelector> elementSelectors) {
    for (int i = 0; i < elementSelectors.size(); i++) {
      if (!elementSelectors.get(i).test(elementInstances.get(i))) {
        return false;
      }
    }
    return true;
  }

  private void awaitElementInstanceAssertion(
      final Consumer<ElementInstanceFilter> filter,
      final Consumer<List<ElementInstance>> assertion) {

    awaitBehavior.untilAsserted(() -> dataSource.findElementInstances(filter), assertion);
  }

  private static List<ElementInstance> getInstancesInState(
      final List<ElementInstance> elementInstances, final ElementInstanceState state) {
    return elementInstances.stream()
        .filter(elementInstance -> elementInstance.getState().equals(state))
        .collect(Collectors.toList());
  }

  private static List<ElementSelector> getSelectorsWithoutInstances(
      final List<ElementSelector> elementSelectors, final List<ElementInstance> elementInstances) {
    return elementSelectors.stream()
        .filter(elementSelector -> elementInstances.stream().noneMatch(elementSelector::test))
        .collect(Collectors.toList());
  }

  private static List<ElementSelector> getSelectorsWithInstances(
      final List<ElementSelector> elementSelectors, final List<ElementInstance> elementInstances) {
    return elementSelectors.stream()
        .filter(elementSelector -> elementInstances.stream().anyMatch(elementSelector::test))
        .collect(Collectors.toList());
  }

  private static String formatElementInstanceStates(
      final List<ElementSelector> elementSelectors, final List<ElementInstance> elementInstances) {

    return elementSelectors.stream()
        .map(
            elementSelector -> {
              final ElementInstanceState elementInstanceState =
                  getElementInstanceStateForSelector(elementInstances, elementSelector);

              return String.format(
                  "\t- '%s': %s", elementSelector.describe(), formatState(elementInstanceState));
            })
        .collect(Collectors.joining("\n"));
  }

  private static ElementInstanceState getElementInstanceStateForSelector(
      final List<ElementInstance> elementInstances, final ElementSelector elementSelector) {

    return elementInstances.stream()
        .filter(elementSelector::test)
        .findFirst()
        .map(ElementInstance::getState)
        .orElse(ElementInstanceState.UNKNOWN_ENUM_VALUE);
  }

  private static String formatElementInstanceStates(
      final List<ElementInstance> elementInstances,
      final Function<ElementInstance, String> elementDescriptor) {

    return elementInstances.stream()
        .map(
            elementInstance ->
                String.format(
                    "\t- '%s': %s",
                    elementDescriptor.apply(elementInstance),
                    formatState(elementInstance.getState())))
        .collect(Collectors.joining("\n"));
  }

  private static String formatState(final ElementInstanceState state) {
    if (state == null || state == ElementInstanceState.UNKNOWN_ENUM_VALUE) {
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

  private static Consumer<ElementInstanceFilter> processInstanceFilter(
      final long processInstanceKey) {
    return filter -> filter.processInstanceKey(processInstanceKey);
  }

  private static Consumer<ElementInstanceFilter> elementInstanceFilter(
      final long processInstanceKey, final List<ElementSelector> elementSelectors) {
    return elementSelectors.size() == 1
        ? processInstanceFilter(processInstanceKey).andThen(elementSelectors.get(0)::applyFilter)
        : processInstanceFilter(processInstanceKey);
  }
}
