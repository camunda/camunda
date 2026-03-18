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

import io.camunda.process.test.api.assertions.EvaluationAssert;
import io.camunda.process.test.api.judge.JudgeConfig;
import java.util.function.UnaryOperator;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ implementation of {@link EvaluationAssert}. Evaluates string values against
 * natural-language expectations using an LLM judge.
 */
public class EvaluationAssertj extends AbstractAssert<EvaluationAssertj, String>
    implements EvaluationAssert {

  private JudgeConfig judgeConfig;

  public EvaluationAssertj(final String actual, final JudgeConfig judgeConfig) {
    super(actual, EvaluationAssertj.class);
    this.judgeConfig = judgeConfig;
  }

  @Override
  public EvaluationAssert withJudgeConfig(final UnaryOperator<JudgeConfig> modifier) {
    if (modifier == null) {
      throw new IllegalArgumentException("modifier must not be null");
    }
    final JudgeConfig base = judgeConfig != null ? judgeConfig : JudgeConfig.defaults();
    judgeConfig = modifier.apply(base);
    return this;
  }

  @Override
  public EvaluationAssert satisfiesExpectation(final String expectation) {
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
