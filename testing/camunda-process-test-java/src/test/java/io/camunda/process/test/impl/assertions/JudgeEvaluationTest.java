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

import java.util.Optional;
import org.junit.jupiter.api.Test;

class JudgeEvaluationTest {

  @Test
  void shouldParseMarkdownWrappedJsonResponse() {
    // given
    final String markdownResponse =
        "```json\n{\"score\": 0.85, \"reasoning\": \"Good match.\"}\n```";
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(prompt -> markdownResponse, "some expectation", Optional.empty());

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
            prompt -> "{\"score\": 1.5, \"reasoning\": \"Over the top.\"}",
            "expectation",
            Optional.empty());

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
            prompt -> "{\"score\": -0.3, \"reasoning\": \"Negative.\"}",
            "expectation",
            Optional.empty());

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
            Optional.empty());

    // when / then
    assertThatThrownBy(() -> evaluation.evaluate("some value"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Connection refused");
  }

  @Test
  void shouldThrowParseExceptionWhenLlmReturnsEmptyResponse() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(prompt -> "", "expectation", Optional.empty());

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
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(prompt -> null, "expectation", Optional.empty());

    // when / then
    assertThatThrownBy(() -> evaluation.evaluate("some value"))
        .isInstanceOf(JudgeResponseParseException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .cause()
        .hasMessageContaining("Empty response from judge");
  }

  @Test
  void shouldRoundScoreToTwoDecimalPlaces() {
    // given — 0.695 is the HALF_UP tie case: third decimal exactly 5 rounds up
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.695, "reasoning");

    // then
    assertThat(result.getScore()).isEqualTo(0.70);
  }

  @Test
  void shouldPassWhenScoreRoundsUpToThreshold() {
    // given — 0.695 rounds to 0.70, which meets the threshold
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.695, "reasoning");

    // then
    assertThat(result.passed(0.70)).isTrue();
  }

  @Test
  void shouldNotPassWhenScoreRoundsDownBelowThreshold() {
    // given — raw score 0.6940 rounds to 0.69, which is below the threshold
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.6940, "reasoning");

    // then
    assertThat(result.passed(0.70)).isFalse();
  }

  @Test
  void shouldPassWhenThresholdRoundsDownToScore() {
    // given — score 0.70, threshold 0.7049 rounds down to 0.70 → passes
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.70, "reasoning");

    // then
    assertThat(result.passed(0.7049)).isTrue();
  }

  @Test
  void shouldNotPassWhenThresholdRoundsUpAboveScore() {
    // given — score 0.70, threshold 0.705 rounds up to 0.71 → fails
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.70, "reasoning");

    // then
    assertThat(result.passed(0.705)).isFalse();
  }
}
