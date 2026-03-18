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
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertion for evaluating arbitrary string values using an LLM judge. This assertion is
 * standalone and not tied to process instance variables.
 *
 * <p>Example usage:
 *
 * <pre>
 *   EvaluationAssertions.assertThat("Hello, how can I help you?")
 *       .satisfiesExpectation("should be a polite greeting");
 *
 *   EvaluationAssertions.assertThat(agentResponse)
 *       .withJudgeConfig(config -&gt; config.withThreshold(0.8))
 *       .satisfiesExpectation("should identify suspicious entries")
 *       .satisfiesExpectation("should recommend an audit");
 * </pre>
 */
public class JudgeEvaluationResultAssert
    extends AbstractAssert<JudgeEvaluationResultAssert, String> {

  private JudgeConfig judgeConfig;

  public JudgeEvaluationResultAssert(final String actual, final JudgeConfig judgeConfig) {
    super(actual, JudgeEvaluationResultAssert.class);
    this.judgeConfig = judgeConfig;
  }

  /**
   * Modifies the current {@link JudgeConfig} for subsequent judge evaluations in this chain. The
   * modifier receives the current config (either the global default or a previously set override)
   * and returns a new config. The global default is not affected.
   *
   * <p>Example usage:
   *
   * <pre>
   *   EvaluationAssertions.assertThat("some text")
   *       .withJudgeConfig(config -&gt; config.withThreshold(0.8))
   *       .satisfiesExpectation("should be professional");
   * </pre>
   *
   * @param modifier a function that receives the current judge config and returns a modified one
   * @return this assertion object
   */
  public JudgeEvaluationResultAssert withJudgeConfig(final UnaryOperator<JudgeConfig> modifier) {
    if (modifier == null) {
      throw new IllegalArgumentException("modifier must not be null");
    }
    final JudgeConfig base = judgeConfig != null ? judgeConfig : JudgeConfig.defaults();
    judgeConfig = modifier.apply(base);
    return this;
  }

  /**
   * Verifies that the actual value satisfies a natural language expectation using an LLM judge.
   * Uses the threshold from the configured {@link JudgeConfig}.
   *
   * @param expectation the natural language expectation
   * @return this assertion object
   */
  public JudgeEvaluationResultAssert satisfiesExpectation(final String expectation) {
    assertJudgeHasAllRequiredSettings();
    assertExpectationNotEmpty(expectation);

    final JudgeEvaluation evaluation =
        new JudgeEvaluation(judgeConfig.getChatModel(), expectation, judgeConfig.getCustomPrompt());

    try {
      final JudgeEvaluation.Result result = evaluation.evaluate(actual);
      final double threshold = judgeConfig.getThreshold();

      if (!result.passed(threshold)) {
        fail(
            "Judge evaluation did not satisfy expectation.\n"
                + "  Expectation: %s\n"
                + "  Actual value: %s\n"
                + "  Score: %.2f (threshold: %.2f)\n"
                + "  Reasoning: %s",
            expectation, actual, result.getScore(), threshold, result.getReasoning());
      }
    } catch (final JudgeResponseParseException e) {
      fail(
          "Judge evaluation failed.\n"
              + "  The judge LLM returned an unparseable response.\n"
              + "  Cause: %s\n"
              + "  Raw response: %s",
          e.getCause().getMessage(), e.getRawResponse());
    }

    return this;
  }

  private void assertJudgeHasAllRequiredSettings() {
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

  private static void assertExpectationNotEmpty(final String expectation) {
    if (expectation == null || expectation.trim().isEmpty()) {
      throw new IllegalArgumentException("expectation must not be null or empty");
    }
  }
}
