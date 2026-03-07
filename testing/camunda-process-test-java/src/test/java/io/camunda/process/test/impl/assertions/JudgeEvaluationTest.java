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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JudgeEvaluationTest {

  @Test
  void shouldParseMarkdownWrappedJsonResponse() {
    // given
    final String markdownResponse =
        "```json\n{\"score\": 0.85, \"reasoning\": \"Good match.\"}\n```";
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(prompt -> markdownResponse, "some expectation", null);

    // when
    final JudgeEvaluation.Result result = evaluation.evaluate("some value");

    // then
    assertThat(result.getScore()).isEqualTo(0.85);
    assertThat(result.getReasoning()).isEqualTo("Good match.");
  }

  @Test
  void shouldClampScoreAboveOneToOne() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(
            prompt -> "{\"score\": 1.5, \"reasoning\": \"Over the top.\"}", "expectation", null);

    // when
    final JudgeEvaluation.Result result = evaluation.evaluate("some value");

    // then
    assertThat(result.getScore()).isEqualTo(1.0);
  }

  @Test
  void shouldClampNegativeScoreToZero() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(
            prompt -> "{\"score\": -0.3, \"reasoning\": \"Negative.\"}", "expectation", null);

    // when
    final JudgeEvaluation.Result result = evaluation.evaluate("some value");

    // then
    assertThat(result.getScore()).isEqualTo(0.0);
  }

  @Test
  void shouldPropagateExceptionWhenLlmCallFails() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(
            prompt -> {
              throw new RuntimeException("Connection refused");
            },
            "expectation",
            null);

    // when / then
    assertThatThrownBy(() -> evaluation.evaluate("some value"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Connection refused");
  }

  @Test
  void shouldThrowParseExceptionWhenLlmReturnsEmptyResponse() {
    // given
    final JudgeEvaluation evaluation = new JudgeEvaluation(prompt -> "", "expectation", null);

    // when / then
    assertThatThrownBy(() -> evaluation.evaluate("some value"))
        .isInstanceOf(JudgeResponseParseException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .cause()
        .hasMessageContaining("Empty response from judge");
  }

  @Test
  void shouldThrowParseExceptionWhenLlmReturnsNullResponse() {
    // given
    final JudgeEvaluation evaluation = new JudgeEvaluation(prompt -> null, "expectation", null);

    // when / then
    assertThatThrownBy(() -> evaluation.evaluate("some value"))
        .isInstanceOf(JudgeResponseParseException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .cause()
        .hasMessageContaining("Empty response from judge");
  }
}
