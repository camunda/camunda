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
import io.camunda.process.test.api.assertions.VariableSelector;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper.JsonMappingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableAssertj extends AbstractAssert<VariableAssertj, String> {

  private static final Logger LOG = LoggerFactory.getLogger(VariableAssertj.class);

  private final CamundaDataSource dataSource;
  private final Supplier<CamundaAssertAwaitBehavior> awaitBehavior;
  private final CamundaAssertJsonMapper jsonMapper;
  private final JudgeAssertj judgeAssertj;
  private final SemanticSimilarityAssertj similarityAssertj;

  public VariableAssertj(
      final CamundaDataSource dataSource,
      final Supplier<CamundaAssertAwaitBehavior> awaitBehavior,
      final CamundaAssertJsonMapper jsonMapper,
      final JudgeConfig judgeConfig,
      final SemanticSimilarityConfig semanticSimilarityConfig,
      final String failureMessagePrefix) {
    super(failureMessagePrefix, VariableAssertj.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
    this.jsonMapper = jsonMapper;
    judgeAssertj = new JudgeAssertj(judgeConfig);
    similarityAssertj = new SemanticSimilarityAssertj(semanticSimilarityConfig);
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

    awaitBehavior
        .get()
        .untilAsserted(
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
      final ElementSelector elementSelector,
      final VariableSelector variableSelector,
      final Object variableValue) {

    withLocalVariableAssertion(
        processInstanceKey,
        elementSelector,
        instance ->
            hasVariable(
                variableSelector,
                variableValue,
                () ->
                    findLocalVariablesBySelector(
                        processInstanceKey, instance.getElementInstanceKey(), variableSelector)));
  }

  public void hasVariable(
      final long processInstanceKey,
      final VariableSelector variableSelector,
      final Object variableValue) {

    hasVariable(
        variableSelector,
        variableValue,
        () -> findGlobalVariablesBySelector(processInstanceKey, variableSelector));
  }

  private void hasVariable(
      final VariableSelector variableSelector,
      final Object variableValue,
      final Supplier<List<Variable>> actualVariablesSupplier) {
    final JsonNode expectedValue = jsonMapper.toJsonNode(variableValue);

    awaitBehavior
        .get()
        .untilAsserted(
            () -> {
              final List<Variable> variables = actualVariablesSupplier.get();

              final Optional<Variable> matchingVariable =
                  variables.stream().filter(variableSelector::test).findFirst();

              assertThat(matchingVariable)
                  .withFailMessage(
                      "%s should have a variable '%s' with value '%s' but the variable doesn't exist.",
                      actual, variableSelector.describe(), expectedValue)
                  .isPresent();

              final JsonNode actualValue = jsonMapper.readJson(matchingVariable.get().getValue());
              assertThat(actualValue)
                  .withFailMessage(
                      "%s should have a variable '%s' with value '%s' but was '%s'.",
                      actual, variableSelector.describe(), expectedValue, actualValue)
                  .isEqualTo(expectedValue);
            });
  }

  public <T> void hasLocalVariableSatisfies(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final VariableSelector variableSelector,
      final Class<T> variableValueType,
      final ThrowingConsumer<T> requirement) {

    withLocalVariableAssertion(
        processInstanceKey,
        elementSelector,
        instance ->
            hasVariableSatisfies(
                variableSelector,
                variableValueType,
                requirement,
                () ->
                    findLocalVariablesBySelector(
                        processInstanceKey, instance.getElementInstanceKey(), variableSelector)));
  }

  public <T> void hasVariableSatisfies(
      final long processInstanceKey,
      final VariableSelector variableSelector,
      final Class<T> variableValueType,
      final ThrowingConsumer<T> requirement) {

    hasVariableSatisfies(
        variableSelector,
        variableValueType,
        requirement,
        () -> findGlobalVariablesBySelector(processInstanceKey, variableSelector));
  }

  private void hasVariableSatisfies(
      final VariableSelector variableSelector,
      final ThrowingConsumer<String> rawRequirement,
      final Supplier<List<Variable>> actualVariablesSupplier) {

    awaitBehavior
        .get()
        .untilAsserted(
            () -> {
              final String rawValue =
                  assertVariableExists(variableSelector, actualVariablesSupplier);
              rawRequirement.accept(rawValue);
            });
  }

  private <T> void hasVariableSatisfies(
      final VariableSelector variableSelector,
      final Class<T> variableValueType,
      final ThrowingConsumer<T> requirement,
      final Supplier<List<Variable>> actualVariablesSupplier) {

    hasVariableSatisfies(
        variableSelector,
        rawValue -> {
          try {
            final T actualValue = jsonMapper.readJson(rawValue, variableValueType);
            requirement.accept(actualValue);
          } catch (final AssertionError e) {
            fail(
                "%s should have a variable '%s' but the following requirement was not satisfied: %s.",
                actual, variableSelector.describe(), e.getMessage());
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
                    actual,
                    variableSelector.describe(),
                    variableValueType.getName(),
                    e.getMessage(),
                    reason);

            fail(failureMessage);
          }
        },
        actualVariablesSupplier);
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
                Collectors.toMap(Entry::getKey, entry -> jsonMapper.toJsonNode(entry.getValue())));

    final Set<String> expectedVariableNames = expectedVariables.keySet();

    awaitBehavior
        .get()
        .untilAsserted(
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
    awaitBehavior.get().untilAsserted(() -> dataSource.findElementInstances(filter), assertion);
  }

  // --- Judge evaluation methods ---

  void withJudgeConfig(final UnaryOperator<JudgeConfig> modifier) {
    judgeAssertj.withJudgeConfig(modifier);
  }

  public void hasVariableSatisfiesJudge(
      final long processInstanceKey,
      final VariableSelector variableSelector,
      final String expectation) {

    judgeAssertj.assertJudgeHasAllRequiredSettings();
    assertExpectationNotEmpty(expectation);

    final String rawValue =
        waitForVariable(
            variableSelector,
            () -> findGlobalVariablesBySelector(processInstanceKey, variableSelector));

    evaluateJudge(variableSelector, expectation, rawValue);
  }

  public void hasLocalVariableSatisfiesJudge(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final VariableSelector variableSelector,
      final String expectation) {

    judgeAssertj.assertJudgeHasAllRequiredSettings();
    assertExpectationNotEmpty(expectation);

    final String rawValue =
        waitForLocalVariable(processInstanceKey, elementSelector, variableSelector);

    evaluateJudge(variableSelector, expectation, rawValue);
  }

  private void evaluateJudge(
      final VariableSelector variableSelector, final String expectation, final String rawValue) {
    judgeAssertj.evaluateExpectation(
        expectation,
        rawValue,
        String.format(" for %s variable '%s'", actual, variableSelector.describe()));
  }

  private String assertVariableExists(
      final VariableSelector variableSelector,
      final Supplier<List<Variable>> actualVariablesSupplier) {
    final List<Variable> variables = actualVariablesSupplier.get();
    final Optional<Variable> matchingVariable =
        variables.stream().filter(variableSelector::test).findFirst();
    assertThat(matchingVariable)
        .withFailMessage(
            "%s should have a variable '%s', but the variable doesn't exist.",
            actual, variableSelector.describe())
        .isPresent();
    return matchingVariable.get().getValue();
  }

  private String waitForVariable(
      final VariableSelector variableSelector,
      final Supplier<List<Variable>> actualVariablesSupplier) {
    final AtomicReference<String> result = new AtomicReference<>();
    awaitBehavior
        .get()
        .untilAsserted(
            () -> result.set(assertVariableExists(variableSelector, actualVariablesSupplier)));
    return result.get();
  }

  private String waitForLocalVariable(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final VariableSelector variableSelector) {
    final AtomicReference<String> result = new AtomicReference<>();
    withLocalVariableAssertion(
        processInstanceKey,
        elementSelector,
        instance ->
            result.set(
                assertVariableExists(
                    variableSelector,
                    () ->
                        findLocalVariablesBySelector(
                            processInstanceKey,
                            instance.getElementInstanceKey(),
                            variableSelector))));
    return result.get();
  }

  // --- Semantic similarity evaluation methods ---

  SemanticSimilarityConfig getSemanticSimilarityConfig() {
    return similarityAssertj.getSemanticSimilarityConfig();
  }

  void withSemanticSimilarityConfig(final UnaryOperator<SemanticSimilarityConfig> modifier) {
    similarityAssertj.withSemanticSimilarityConfig(modifier);
  }

  public void hasVariableSimilarTo(
      final long processInstanceKey,
      final VariableSelector variableSelector,
      final String expectedValue) {

    similarityAssertj.assertSimilarityHasAllRequiredSettings();
    assertExpectationNotEmpty(expectedValue);

    hasVariableSatisfies(
        variableSelector,
        rawValue -> evaluateSimilarity(variableSelector.describe(), expectedValue, rawValue),
        () -> findGlobalVariablesBySelector(processInstanceKey, variableSelector));
  }

  public void hasLocalVariableSimilarTo(
      final long processInstanceKey,
      final ElementSelector selector,
      final VariableSelector variableSelector,
      final String expectedValue) {

    similarityAssertj.assertSimilarityHasAllRequiredSettings();
    assertExpectationNotEmpty(expectedValue);

    withLocalVariableAssertion(
        processInstanceKey,
        selector,
        instance ->
            hasVariableSatisfies(
                variableSelector,
                rawValue ->
                    evaluateSimilarity(variableSelector.describe(), expectedValue, rawValue),
                () ->
                    findLocalVariablesBySelector(
                        processInstanceKey, instance.getElementInstanceKey(), variableSelector)));
  }

  private static void assertExpectationNotEmpty(final String expectation) {
    if (expectation == null || expectation.trim().isEmpty()) {
      throw new IllegalArgumentException("expectation must not be null or empty");
    }
  }

  private void evaluateSimilarity(
      final String variableName, final String expectedValue, final String variableValue) {
    if (variableValue == null || variableValue.trim().isEmpty()) {
      fail(
          "%s variable '%s' is present but has no value to compare for semantic similarity.",
          actual, variableName);
    }
    similarityAssertj.evaluateSimilarity(
        expectedValue, variableValue, String.format("%s variable '%s' ", actual, variableName));
  }

  // --- Variable fetching ---

  private Map<String, String> getGlobalProcessInstanceVariables(final long processInstanceKey) {
    final List<Variable> variables =
        dataSource.findGlobalVariablesByProcessInstanceKey(processInstanceKey);
    return toMap(ensureVariablesAreNotTruncated(variables));
  }

  private Map<String, String> getLocalProcessInstanceVariables(
      final long processInstanceKey, final long elementInstanceKey) {
    final List<Variable> variables =
        dataSource.findVariables(
            filter -> filter.processInstanceKey(processInstanceKey).scopeKey(elementInstanceKey));
    return toMap(ensureVariablesAreNotTruncated(variables));
  }

  private List<Variable> findGlobalVariablesBySelector(
      final long processInstanceKey, final VariableSelector selector) {
    return findLocalVariablesBySelector(processInstanceKey, processInstanceKey, selector);
  }

  private List<Variable> findLocalVariablesBySelector(
      final long processInstanceKey,
      final long elementInstanceKey,
      final VariableSelector selector) {
    return ensureVariablesAreNotTruncated(
        dataSource.findVariables(
            filter ->
                selector.applyFilter(
                    filter.processInstanceKey(processInstanceKey).scopeKey(elementInstanceKey))));
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
