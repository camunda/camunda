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

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper.JsonMappingException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ThrowingConsumer;

public class VariableAssertj extends AbstractAssert<VariableAssertj, String> {

  private final CamundaDataSource dataSource;
  private final CamundaAssertAwaitBehavior awaitBehavior;
  private final CamundaAssertJsonMapper jsonMapper;
  private final VariableFetcher variableFetcher;

  public VariableAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final CamundaAssertJsonMapper jsonMapper,
      final String failureMessagePrefix) {
    super(failureMessagePrefix, VariableAssertj.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
    this.jsonMapper = jsonMapper;
    this.variableFetcher = new VariableFetcher(dataSource);
  }

  public void hasLocalVariableNames(
      final long processInstanceKey,
      final ElementSelector selector,
      final String... variableNames) {

    withLocalVariableAssertion(
        processInstanceKey,
        selector,
        instance ->
            hasVariableNames(
                () ->
                    variableFetcher.getLocalProcessInstanceVariables(
                        processInstanceKey, instance.getElementInstanceKey()),
                variableNames));
  }

  public void hasVariableNames(final long processInstanceKey, final String... variableNames) {
    hasVariableNames(
        () -> variableFetcher.getGlobalProcessInstanceVariables(processInstanceKey), variableNames);
  }

  private void hasVariableNames(
      final Supplier<Map<String, String>> actualVariablesSupplier, final String... variableNames) {

    awaitBehavior.untilAsserted(
        () -> {
          final Map<String, String> variables = actualVariablesSupplier.get();

          final List<String> missingVariableNames =
              Arrays.stream(variableNames)
                  .filter(variableName -> !variables.containsKey(variableName))
                  .collect(Collectors.toList());

          assertThat(missingVariableNames)
              .withFailMessage(
                  "%s should have the variables %s but %s don't exist.",
                  actual,
                  AssertFormatUtil.formatNames(variableNames),
                  AssertFormatUtil.formatNames(missingVariableNames))
              .isEmpty();
        });
  }

  public void hasLocalVariable(
      final long processInstanceKey,
      final ElementSelector selector,
      final String variableName,
      final Object variableValue) {

    withLocalVariableAssertion(
        processInstanceKey,
        selector,
        instance ->
            hasVariable(
                variableName,
                variableValue,
                () ->
                    variableFetcher.getLocalProcessInstanceVariables(
                        processInstanceKey, instance.getElementInstanceKey())));
  }

  public void hasVariable(
      final long processInstanceKey, final String variableName, final Object variableValue) {

    hasVariable(
        variableName,
        variableValue,
        () -> variableFetcher.getGlobalProcessInstanceVariables(processInstanceKey));
  }

  private void hasVariable(
      final String variableName,
      final Object variableValue,
      final Supplier<Map<String, String>> actualVariablesSupplier) {
    final JsonNode expectedValue = jsonMapper.toJsonNode(variableValue);

    awaitBehavior.untilAsserted(
        () -> {
          final Map<String, String> variables = actualVariablesSupplier.get();

          assertThat(variables)
              .withFailMessage(
                  "%s should have a variable '%s' with value '%s' but the variable doesn't exist.",
                  actual, variableName, expectedValue)
              .containsKey(variableName);

          final JsonNode actualValue = jsonMapper.readJson(variables.get(variableName));
          assertThat(actualValue)
              .withFailMessage(
                  "%s should have a variable '%s' with value '%s' but was '%s'.",
                  actual, variableName, expectedValue, actualValue)
              .isEqualTo(expectedValue);
        });
  }

  public <T> void hasLocalVariableSatisfies(
      final long processInstanceKey,
      final ElementSelector selector,
      final String variableName,
      final Class<T> variableValueType,
      final ThrowingConsumer<T> requirement) {

    withLocalVariableAssertion(
        processInstanceKey,
        selector,
        instance ->
            hasVariableSatisfies(
                variableName,
                variableValueType,
                requirement,
                () ->
                    variableFetcher.getLocalProcessInstanceVariables(
                        processInstanceKey, instance.getElementInstanceKey())));
  }

  public <T> void hasVariableSatisfies(
      final long processInstanceKey,
      final String variableName,
      final Class<T> variableValueType,
      final ThrowingConsumer<T> requirement) {

    hasVariableSatisfies(
        variableName,
        variableValueType,
        requirement,
        () -> variableFetcher.getGlobalProcessInstanceVariables(processInstanceKey));
  }

  private <T> void hasVariableSatisfies(
      final String variableName,
      final Class<T> variableValueType,
      final ThrowingConsumer<T> requirement,
      final Supplier<Map<String, String>> actualVariablesSupplier) {

    awaitBehavior.untilAsserted(
        () -> {
          final Map<String, String> variables = actualVariablesSupplier.get();

          assertThat(variables)
              .withFailMessage(
                  "%s should have a variable '%s', but the variable doesn't exist.",
                  actual, variableName)
              .containsKey(variableName);

          final String actualVariable = variables.get(variableName);
          try {
            final T actualValue = jsonMapper.readJson(actualVariable, variableValueType);

            requirement.accept(actualValue);
          } catch (final AssertionError e) {
            fail(
                "%s should have a variable '%s' but the following requirement was not satisfied: %s.",
                actual, variableName, e.getMessage());

          } catch (final JsonMappingException e) {
            final Throwable reason =
                Optional.ofNullable(e.getCause())
                    .map(cause -> Optional.ofNullable(cause.getCause()).orElse(cause))
                    .orElse(e);

            final String failureMessage =
                String.format(
                    "%s should have a variable '%s' of type '%s', but the JSON mapping failed:\n"
                        + "Error: %s\n"
                        + "Reason: %s",
                    actual, variableName, variableValueType.getName(), e.getMessage(), reason);

            fail(failureMessage);
          }
        });
  }

  public void hasLocalVariables(
      final long processInstanceKey,
      final ElementSelector selector,
      final Map<String, Object> expectedVariables) {

    withLocalVariableAssertion(
        processInstanceKey,
        selector,
        instance ->
            hasVariables(
                expectedVariables,
                () ->
                    variableFetcher.getLocalProcessInstanceVariables(
                        processInstanceKey, instance.getElementInstanceKey())));
  }

  public void hasVariables(
      final long processInstanceKey, final Map<String, Object> expectedVariables) {

    hasVariables(
        expectedVariables,
        () -> variableFetcher.getGlobalProcessInstanceVariables(processInstanceKey));
  }

  private void hasVariables(
      final Map<String, Object> expectedVariables,
      final Supplier<Map<String, String>> actualVariablesSupplier) {
    final Map<String, JsonNode> expectedValues =
        expectedVariables.entrySet().stream()
            .collect(
                Collectors.toMap(Entry::getKey, entry -> jsonMapper.toJsonNode(entry.getValue())));

    final Set<String> expectedVariableNames = expectedVariables.keySet();

    awaitBehavior.untilAsserted(
        () -> {
          final Map<String, JsonNode> actualValues =
              actualVariablesSupplier.get().entrySet().stream()
                  .filter(entry -> expectedVariableNames.contains(entry.getKey()))
                  .collect(
                      Collectors.toMap(
                          Entry::getKey, entry -> jsonMapper.readJson(entry.getValue())));

          final List<String> missingVariables =
              expectedVariableNames.stream()
                  .filter(variableName -> !actualValues.containsKey(variableName))
                  .collect(Collectors.toList());

          assertThat(missingVariables)
              .withFailMessage(
                  "%s should have the variables %s but was %s. The variables %s don't exist.",
                  actual,
                  jsonMapper.toJsonNode(expectedVariables),
                  jsonMapper.toJsonNode(actualValues),
                  AssertFormatUtil.formatNames(missingVariables))
              .isEmpty();

          assertThat(actualValues)
              .withFailMessage(
                  "%s should have the variables %s but was %s.",
                  actual,
                  jsonMapper.toJsonNode(expectedVariables),
                  jsonMapper.toJsonNode(actualValues))
              .containsAllEntriesOf(expectedValues);
        });
  }

  private void withLocalVariableAssertion(
      final long processInstanceKey,
      final ElementSelector selector,
      final Consumer<ElementInstance> assertionCallback) {

    awaitElementInstanceAssertion(
        f -> {
          f.processInstanceKey(processInstanceKey);
          selector.applyFilter(f);
        },
        elementInstances -> {
          final Optional<ElementInstance> instance =
              elementInstances.stream().filter(selector::test).findFirst();

          assertThat(instance)
              .withFailMessage(
                  "No element [%s] found for process instance [key: %s]",
                  selector.describe(), processInstanceKey)
              .isPresent();

          assertionCallback.accept(instance.get());
        });
  }

  private void awaitElementInstanceAssertion(
      final Consumer<ElementInstanceFilter> filter,
      final Consumer<List<ElementInstance>> assertion) {
    awaitBehavior.untilAsserted(() -> dataSource.findElementInstances(filter), assertion);
  }
}
