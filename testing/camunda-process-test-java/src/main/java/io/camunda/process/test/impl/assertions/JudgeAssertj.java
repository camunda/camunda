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

import static org.assertj.core.api.Assertions.fail;

import io.camunda.process.test.api.judge.JudgeConfig;
import java.util.function.UnaryOperator;

/**
 * Reusable component that provides LLM judge evaluation capabilities. Intended to be composed into
 * assertion classes that need judge-based evaluation.
 */
class JudgeAssertj {

  private JudgeConfig judgeConfig;

  JudgeAssertj(final JudgeConfig judgeConfig) {
    this.judgeConfig = judgeConfig;
  }

  /**
   * Overrides the {@link JudgeConfig}. The modifier receives the current config and returns a new
   * one.
   *
   * @param modifier function that transforms the current config
   */
  void withJudgeConfig(final UnaryOperator<JudgeConfig> modifier) {
    if (modifier == null) {
      throw new IllegalArgumentException("modifier must not be null");
    }
    final JudgeConfig base = judgeConfig != null ? judgeConfig : JudgeConfig.defaults();
    final JudgeConfig modified = modifier.apply(base);
    if (modified == null) {
      throw new IllegalArgumentException("modifier must not return null");
    }
    judgeConfig = modified;
  }

  /**
   * Validates that the judge config has all required settings for evaluation.
   *
   * @throws IllegalStateException if judgeConfig or its chatModel is null
   */
  void assertJudgeHasAllRequiredSettings() {
    if (judgeConfig == null) {
      throw new IllegalStateException(
          "JudgeConfig is not set. Ensure to provide a JudgeConfig instance to use judge assertions.");
    }
    if (judgeConfig.getChatModel() == null) {
      throw new IllegalStateException(
          "JudgeConfig has no ChatModelAdapter configured. "
              + "Use JudgeConfig.of(chatModel) or withJudgeConfig(config -> config.withChatModelAdapter(chatModel)).");
    }
  }

  /**
   * Validates that the expectation string is not null or blank.
   *
   * @param expectation the expectation to validate
   * @throws IllegalArgumentException if the expectation is null or blank
   */
  static void assertExpectationNotEmpty(final String expectation) {
    if (expectation == null || expectation.trim().isEmpty()) {
      throw new IllegalArgumentException("expectation must not be null or empty");
    }
  }

  /**
   * Evaluates the actual value against the expectation using the configured LLM judge.
   *
   * @param expectation the natural-language expectation
   * @param actualValue the value to evaluate
   * @param context additional context appended to error messages (e.g. {@code " for variable
   *     'myVar'"}), or empty string for no context
   */
  void evaluateExpectation(
      final String expectation, final String actualValue, final String context) {

    final JudgeEvaluation evaluation =
        new JudgeEvaluation(judgeConfig.getChatModel(), expectation, judgeConfig.getCustomPrompt());

    try {
      final JudgeEvaluation.Result result = evaluation.evaluate(actualValue);
      final double threshold = judgeConfig.getThreshold();

      if (!result.passed(threshold)) {
        fail(
            "Judge evaluation did not satisfy expectation%s.\n"
                + "  Expectation: %s\n"
                + "  Actual value: %s\n"
                + "  Score: %.2f (threshold: %.2f)\n"
                + "  Reasoning: %s",
            context, expectation, actualValue, result.getScore(), threshold, result.getReasoning());
      }
    } catch (final JudgeResponseParseException e) {
      fail(
          "Judge evaluation failed%s.\n"
              + "  The judge LLM returned an unparseable response.\n"
              + "  Cause: %s\n"
              + "  Raw response: %s",
          context, e.getCause().getMessage(), e.getRawResponse());
    }
  }
}
