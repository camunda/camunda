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
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.impl.assertions.util.AssertionJsonMapper;
import io.camunda.process.test.impl.assertions.util.AssertionJsonMapper.JsonMappingException;
import java.util.Arrays;
import java.util.HashMap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableAssertj extends AbstractAssert<VariableAssertj, String> {

  private static final Logger LOG = LoggerFactory.getLogger(VariableAssertj.class);

  private final CamundaDataSource dataSource;
  private final CamundaAssertAwaitBehavior awaitBehavior;

  public VariableAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final String failureMessagePrefix) {
    super(failureMessagePrefix, VariableAssertj.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
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
                    getLocalProcessInstanceVariables(
                        processInstanceKey, instance.getElementInstanceKey()),
                variableNames));
  }

  public void hasVariableNames(final long processInstanceKey, final String... variableNames) {
    hasVariableNames(() -> getGlobalProcessInstanceVariables(processInstanceKey), variableNames);
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
                    getLocalProcessInstanceVariables(
                        processInstanceKey, instance.getElementInstanceKey())));
  }

  public void hasVariable(
      final long processInstanceKey, final String variableName, final Object variableValue) {

    hasVariable(
        variableName, variableValue, () -> getGlobalProcessInstanceVariables(processInstanceKey));
  }

  private void hasVariable(
      final String variableName,
      final Object variableValue,
      final Supplier<Map<String, String>> actualVariablesSupplier) {
    final JsonNode expectedValue = AssertionJsonMapper.toJson(variableValue);

    awaitBehavior.untilAsserted(
        () -> {
          final Map<String, String> variables = actualVariablesSupplier.get();

          assertThat(variables)
              .withFailMessage(
                  "%s should have a variable '%s' with value '%s' but the variable doesn't exist.",
                  actual, variableName, expectedValue)
              .containsKey(variableName);

          final JsonNode actualValue = AssertionJsonMapper.readJson(variables.get(variableName));
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
                    getLocalProcessInstanceVariables(
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
        () -> getGlobalProcessInstanceVariables(processInstanceKey));
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
            final T actualValue = AssertionJsonMapper.readJson(actualVariable, variableValueType);

            requirement.accept(actualValue);
          } catch (final AssertionError e) {
            fail(
                "%s should have a variable '%s' but the following requirement was not satisfied: %s.",
                actual, variableName, e.getMessage());

          } catch (final JsonMappingException e) {
            final String failureMessage =
                String.format(
                    "%s should have a variable '%s' of type '%s', but was: '%s'",
                    actual, variableName, variableValueType.getName(), actualVariable);

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
                    getLocalProcessInstanceVariables(
                        processInstanceKey, instance.getElementInstanceKey())));
  }

  public void hasVariables(
      final long processInstanceKey, final Map<String, Object> expectedVariables) {

    hasVariables(expectedVariables, () -> getGlobalProcessInstanceVariables(processInstanceKey));
  }

  private void hasVariables(
      final Map<String, Object> expectedVariables,
      final Supplier<Map<String, String>> actualVariablesSupplier) {
    final Map<String, JsonNode> expectedValues =
        expectedVariables.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey, entry -> AssertionJsonMapper.toJson(entry.getValue())));

    final Set<String> expectedVariableNames = expectedVariables.keySet();

    awaitBehavior.untilAsserted(
        () -> {
          final Map<String, JsonNode> actualValues =
              actualVariablesSupplier.get().entrySet().stream()
                  .filter(entry -> expectedVariableNames.contains(entry.getKey()))
                  .collect(
                      Collectors.toMap(
                          Entry::getKey, entry -> AssertionJsonMapper.readJson(entry.getValue())));

          final List<String> missingVariables =
              expectedVariableNames.stream()
                  .filter(variableName -> !actualValues.containsKey(variableName))
                  .collect(Collectors.toList());

          assertThat(missingVariables)
              .withFailMessage(
                  "%s should have the variables %s but was %s. The variables %s don't exist.",
                  actual,
                  AssertionJsonMapper.toJson(expectedVariables),
                  AssertionJsonMapper.toJson(actualValues),
                  AssertFormatUtil.formatNames(missingVariables))
              .isEmpty();

          assertThat(actualValues)
              .withFailMessage(
                  "%s should have the variables %s but was %s.",
                  actual,
                  AssertionJsonMapper.toJson(expectedVariables),
                  AssertionJsonMapper.toJson(actualValues))
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

  private Map<String, String> getLocalProcessInstanceVariables(
      final long processInstanceKey, final long elementInstanceKey) {

    final List<Variable> variables =
        dataSource.findVariables(
            filter -> filter.processInstanceKey(processInstanceKey).scopeKey(elementInstanceKey));

    return toMap(ensureVariablesAreNotTruncated(variables));
  }

  private Map<String, String> getGlobalProcessInstanceVariables(final long processInstanceKey) {
    final List<Variable> variables =
        dataSource.findGlobalVariablesByProcessInstanceKey(processInstanceKey);

    return toMap(ensureVariablesAreNotTruncated(variables));
  }

  private List<Variable> ensureVariablesAreNotTruncated(final List<Variable> variablesToCheck) {
    return variablesToCheck.stream()
        .map(
            variable -> {
              if (variable.isTruncated()) {
                return fetchCompleteVariableByKey(variable);
              } else {
                return variable;
              }
            })
        .collect(Collectors.toList());
  }

  private Variable fetchCompleteVariableByKey(final Variable variable) {
    try {

      return dataSource.getVariable(variable.getVariableKey());
    } catch (final Throwable t) {

      final String expandVariableException =
          String.format(
              "Unable to fetch complete variable data for truncated variable [name: %s]. Will attempt to "
                  + "complete the assertion based on the truncated value which may lead to errors.",
              variable.getName());
      LOG.warn(expandVariableException, t);

      return variable;
    }
  }

  private Map<String, String> toMap(final List<Variable> variables) {
    return variables.stream()
        // We're deliberately switching from the Collectors.toMap collector to a custom
        // implementation because it's allowed to have Camunda Variables with null values
        // However, the toMap collector does not allow null values and would throw an exception.
        // See this Stack Overflow issue for more context: https://stackoverflow.com/a/24634007
        .collect(HashMap::new, (m, v) -> m.put(v.getName(), v.getValue()), HashMap::putAll);
  }
}
