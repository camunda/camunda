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

import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.judge.JudgeConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.assertj.core.api.AbstractAssert;

class JudgeAssertj extends AbstractAssert<JudgeAssertj, String> {

  private final CamundaDataSource dataSource;
  private final CamundaAssertAwaitBehavior awaitBehavior;
  private final JudgeConfig judgeConfig;
  private final VariableFetcher variableFetcher;

  JudgeAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final JudgeConfig judgeConfig,
      final String failureMessagePrefix) {
    super(failureMessagePrefix, JudgeAssertj.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
    this.judgeConfig = judgeConfig;
    variableFetcher = new VariableFetcher(dataSource);
  }

  public void hasVariableSatisfiesJudge(
      final long processInstanceKey, final String variableName, final String expectation) {

    assertJudgeConfigured();
    hasVariableSatisfiesJudge(
        processInstanceKey, variableName, expectation, judgeConfig.getThreshold());
  }

  public void hasVariableSatisfiesJudge(
      final long processInstanceKey,
      final String variableName,
      final String expectation,
      final double threshold) {

    assertJudgeConfigured();
    assertExpectationNotEmpty(expectation);
    assertThresholdInRange(threshold);
    hasVariableSatisfiesJudge(
        variableName,
        expectation,
        threshold,
        () -> variableFetcher.getGlobalProcessInstanceVariables(processInstanceKey));
  }

  public void hasLocalVariableSatisfiesJudge(
      final long processInstanceKey,
      final ElementSelector selector,
      final String variableName,
      final String expectation) {

    assertJudgeConfigured();
    hasLocalVariableSatisfiesJudge(
        processInstanceKey, selector, variableName, expectation, judgeConfig.getThreshold());
  }

  public void hasLocalVariableSatisfiesJudge(
      final long processInstanceKey,
      final ElementSelector selector,
      final String variableName,
      final String expectation,
      final double threshold) {

    assertJudgeConfigured();
    assertExpectationNotEmpty(expectation);
    assertThresholdInRange(threshold);

    final AtomicReference<String> capturedValue = new AtomicReference<>();
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

          final Map<String, String> variables =
              variableFetcher.getLocalProcessInstanceVariables(
                  processInstanceKey, instance.get().getElementInstanceKey());

          assertThat(variables)
              .withFailMessage(
                  "%s should have a variable '%s' but the variable doesn't exist.",
                  actual, variableName)
              .containsKey(variableName);

          capturedValue.set(variables.get(variableName));
        });

    evaluateJudge(variableName, expectation, threshold, capturedValue.get());
  }

  private void hasVariableSatisfiesJudge(
      final String variableName,
      final String expectation,
      final double threshold,
      final Supplier<Map<String, String>> actualVariablesSupplier) {

    final AtomicReference<String> capturedValue = new AtomicReference<>();
    awaitBehavior.untilAsserted(
        () -> {
          final Map<String, String> variables = actualVariablesSupplier.get();

          assertThat(variables)
              .withFailMessage(
                  "%s should have a variable '%s' but the variable doesn't exist.",
                  actual, variableName)
              .containsKey(variableName);

          capturedValue.set(variables.get(variableName));
        });

    evaluateJudge(variableName, expectation, threshold, capturedValue.get());
  }

  private void evaluateJudge(
      final String variableName,
      final String expectation,
      final double threshold,
      final String variableValue) {

    final JudgeEvaluation evaluation =
        new JudgeEvaluation(judgeConfig.getChatModel(), expectation, judgeConfig.getCustomPrompt());

    try {
      final JudgeEvaluation.Result result = evaluation.evaluate(variableValue);

      if (!result.passed(threshold)) {
        fail(
            "%s variable '%s' did not satisfy judge expectation.\n"
                + "  Expectation: %s\n"
                + "  Actual value: %s\n"
                + "  Score: %.2f (threshold: %.2f)\n"
                + "  Reasoning: %s",
            actual,
            variableName,
            expectation,
            variableValue,
            result.getScore(),
            threshold,
            result.getReasoning());
      }
    } catch (final JudgeResponseParseException e) {
      fail(
          "%s judge evaluation failed for variable '%s'.\n"
              + "  The judge LLM returned an unparseable response.\n"
              + "  Cause: %s\n"
              + "  Raw response: %s",
          actual, variableName, e.getCause().getMessage(), e.getRawResponse());
    }
  }

  private void assertJudgeConfigured() {
    if (judgeConfig == null) {
      throw new IllegalStateException(
          "JudgeConfig is not set. Ensure to provide a JudgeConfig instance to use judge assertions.");
    }
  }

  private static void assertExpectationNotEmpty(final String expectation) {
    if (expectation == null || expectation.trim().isEmpty()) {
      throw new IllegalArgumentException("expectation must not be null or empty");
    }
  }

  private static void assertThresholdInRange(final double threshold) {
    if (threshold < 0.0 || threshold > 1.0) {
      throw new IllegalArgumentException(
          "threshold must be between 0.0 and 1.0, was: " + threshold);
    }
  }

  private void awaitElementInstanceAssertion(
      final Consumer<ElementInstanceFilter> filter,
      final Consumer<List<ElementInstance>> assertion) {
    awaitBehavior.untilAsserted(() -> dataSource.findElementInstances(filter), assertion);
  }
}
