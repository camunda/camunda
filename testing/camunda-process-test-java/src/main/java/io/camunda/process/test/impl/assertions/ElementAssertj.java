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
import io.camunda.process.test.impl.client.CamundaClientNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

public class ElementAssertj extends AbstractAssert<ElementAssertj, String> {

  private final CamundaDataSource dataSource;

  protected ElementAssertj(final CamundaDataSource dataSource, final String failureMessagePrefix) {
    super(failureMessagePrefix, ElementAssertj.class);
    this.dataSource = dataSource;
  }

  public void hasActiveElements(final long processInstanceKey, final String... elementIds) {
    hasElementsInState(
        processInstanceKey,
        asElementIdSelectors(elementIds),
        FlowNodeInstanceState.ACTIVE,
        Objects::nonNull);
  }

  public void hasActiveElements(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasElementsInState(
        processInstanceKey,
        Arrays.asList(elementSelectors),
        FlowNodeInstanceState.ACTIVE,
        Objects::nonNull);
  }

  public void hasCompletedElements(final long processInstanceKey, final String... elementIds) {
    hasElementsInState(
        processInstanceKey,
        asElementIdSelectors(elementIds),
        FlowNodeInstanceState.COMPLETED,
        ElementAssertj::isEnded);
  }

  public void hasCompletedElements(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasElementsInState(
        processInstanceKey,
        Arrays.asList(elementSelectors),
        FlowNodeInstanceState.COMPLETED,
        ElementAssertj::isEnded);
  }

  public void hasTerminatedElements(final long processInstanceKey, final String... elementIds) {
    hasElementsInState(
        processInstanceKey,
        asElementIdSelectors(elementIds),
        FlowNodeInstanceState.TERMINATED,
        ElementAssertj::isEnded);
  }

  public void hasTerminatedElements(
      final long processInstanceKey, final ElementSelector... elementSelectors) {
    hasElementsInState(
        processInstanceKey,
        Arrays.asList(elementSelectors),
        FlowNodeInstanceState.TERMINATED,
        ElementAssertj::isEnded);
  }

  private void hasElementsInState(
      final long processInstanceKey,
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
                    dataSource.getFlowNodeInstancesByProcessInstanceKey(processInstanceKey);
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
              actual,
              formatState(expectedState),
              formatElementSelectors(elementSelectors),
              formatState(expectedState),
              elementsNotInState);
      fail(failureMessage);
    }
  }

  private static List<ElementSelector> asElementIdSelectors(final String[] elementIds) {
    return Arrays.stream(elementIds).map(ElementSelectors::byId).collect(Collectors.toList());
  }

  private static boolean isEnded(final FlowNodeInstance flowNodeInstance) {
    return flowNodeInstance.getEndDate() != null;
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
