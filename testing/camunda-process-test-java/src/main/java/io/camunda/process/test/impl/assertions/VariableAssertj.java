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
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.impl.assertions.util.AssertionJsonMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

public class VariableAssertj extends AbstractAssert<VariableAssertj, String> {

  private final CamundaDataSource dataSource;

  public VariableAssertj(final CamundaDataSource dataSource, final String failureMessagePrefix) {
    super(failureMessagePrefix, VariableAssertj.class);
    this.dataSource = dataSource;
  }

  public void hasLocalVariableNames(
      final long processInstanceKey,
      final ElementSelector selector,
      final String... variableNames) {

    withLocalVariableAssertion(
        processInstanceKey,
        selector,
        instance -> {
          hasVariableNames(
              () ->
                  toMap(
                      dataSource.findVariables(
                          filter ->
                              filter
                                  .processInstanceKey(processInstanceKey)
                                  .scopeKey(instance.getElementInstanceKey()))),
              variableNames);
        });
  }

  public void hasVariableNames(final long processInstanceKey, final String... variableNames) {
    hasVariableNames(() -> getGlobalProcessInstanceVariables(processInstanceKey), variableNames);
  }

  private void hasVariableNames(
      final Supplier<Map<String, String>> actualVariablesSupplier, final String... variableNames) {

    final AtomicReference<Map<String, String>> reference =
        new AtomicReference<>(Collections.emptyMap());

    try {
      Awaitility.await()
          .untilAsserted(
              () -> {
                final Map<String, String> variables = actualVariablesSupplier.get();
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
              "%s should have the variables %s but %s don't exist.",
              actual,
              AssertFormatUtil.formatNames(variableNames),
              AssertFormatUtil.formatNames(missingVariableNames));
      fail(failureMessage);
    }
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
                () -> findLocalVariables(processInstanceKey, instance.getElementInstanceKey())));
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

    final AtomicReference<Map<String, String>> reference =
        new AtomicReference<>(Collections.emptyMap());

    try {
      Awaitility.await()
          .untilAsserted(
              () -> {
                final Map<String, String> variables = actualVariablesSupplier.get();
                reference.set(variables);

                assertThat(variables).containsKey(variableName);

                final JsonNode actualValue =
                    AssertionJsonMapper.readJson(variables.get(variableName));
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
              "%s should have a variable '%s' with value '%s' but %s.",
              actual, variableName, expectedValue, failureReason);
      fail(failureMessage);
    }
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
                    toMap(
                        dataSource.findVariables(
                            filter ->
                                filter
                                    .processInstanceKey(processInstanceKey)
                                    .scopeKey(instance.getElementInstanceKey())))));
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

    final AtomicReference<Map<String, JsonNode>> reference =
        new AtomicReference<>(Collections.emptyMap());

    try {
      Awaitility.await()
          .untilAsserted(
              () -> {
                final Map<String, JsonNode> actualValues =
                    actualVariablesSupplier.get().entrySet().stream()
                        .filter(entry -> expectedVariableNames.contains(entry.getKey()))
                        .collect(
                            Collectors.toMap(
                                Entry::getKey,
                                entry -> AssertionJsonMapper.readJson(entry.getValue())));
                reference.set(actualValues);

                assertThat(actualValues).containsAllEntriesOf(expectedValues);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final Map<String, JsonNode> actualVariables = reference.get();

      final List<String> missingVariables =
          expectedVariableNames.stream()
              .filter(variableName -> !actualVariables.containsKey(variableName))
              .collect(Collectors.toList());

      String formattedMissingVariables = "";
      if (!missingVariables.isEmpty()) {
        formattedMissingVariables =
            String.format(
                " The variables %s don't exist.", AssertFormatUtil.formatNames(missingVariables));
      }

      final String failureMessage =
          String.format(
              "%s should have the variables %s but was %s.%s",
              actual,
              AssertionJsonMapper.toJson(expectedVariables),
              AssertionJsonMapper.toJson(actualVariables),
              formattedMissingVariables);
      fail(failureMessage);
    }
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
    // If await() times out, the exception doesn't contain the assertion error. Use a reference to
    // store the error's failure message.
    final AtomicReference<String> failureMessage = new AtomicReference<>("?");
    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> dataSource.findElementInstances(filter),
              elementInstances -> {
                try {
                  assertion.accept(elementInstances);
                } catch (final AssertionError e) {
                  failureMessage.set(e.getMessage());
                  throw e;
                }
              });

    } catch (final ConditionTimeoutException ignore) {
      fail(failureMessage.get());
    }
  }

  private Map<String, String> findLocalVariables(
      final long processInstanceKey, final long elementInstanceKey) {

    return toMap(
        dataSource.findVariables(
            filter -> filter.processInstanceKey(processInstanceKey).scopeKey(elementInstanceKey)));
  }

  private Map<String, String> getGlobalProcessInstanceVariables(final long processInstanceKey) {
    return toMap(dataSource.findGlobalVariablesByProcessInstanceKey(processInstanceKey));
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
