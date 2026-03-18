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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class JudgeEvaluationResultAssertTest {

  @AfterEach
  void resetJudgeConfig() {
    CamundaAssert.setJudgeConfig(null);
  }

  @Nested
  class SatisfiesExpectation {

    @Test
    void shouldPassWhenJudgeScoreAboveThreshold() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.9, \"reasoning\": \"The value matches.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then - should not throw
      EvaluationAssertions.assertThat("Hello, World!").satisfiesExpectation("should be a greeting");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenJudgeScoreBelowThreshold() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.2, \"reasoning\": \"The value does not match.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then
      Assertions.assertThatThrownBy(
              () ->
                  EvaluationAssertions.assertThat("random text")
                      .satisfiesExpectation("should be an email address"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("did not satisfy expectation")
          .hasMessageContaining("Score: 0.20")
          .hasMessageContaining("The value does not match.");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldIncludeActualValueInFailureMessage() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.1, \"reasoning\": \"Not a match.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then
      Assertions.assertThatThrownBy(
              () ->
                  EvaluationAssertions.assertThat("the actual text value")
                      .satisfiesExpectation("should be something else"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("the actual text value")
          .hasMessageContaining("should be something else")
          .hasMessageContaining("Score: 0.10")
          .hasMessageContaining("threshold: 0.50");
    }

    @Test
    void shouldPassWhenScoreExactlyAtThreshold() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.5, \"reasoning\": \"Borderline match.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then - score == threshold (0.5 >= 0.5) should pass
      EvaluationAssertions.assertThat("borderline value").satisfiesExpectation("some expectation");
    }

    @Test
    void shouldPassWithCustomThreshold() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.3, \"reasoning\": \"Low score.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then - should pass with low threshold
      EvaluationAssertions.assertThat("some text")
          .withJudgeConfig(config -> config.withThreshold(0.2))
          .satisfiesExpectation("some expectation");
    }

    @Test
    void shouldSupportChainingMultipleExpectations() {
      // given
      final ChatModelAdapter mockModel = prompt -> "{\"score\": 0.9, \"reasoning\": \"Matches.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then - chaining should work
      EvaluationAssertions.assertThat("Hello, how can I help you today?")
          .satisfiesExpectation("should be a greeting")
          .satisfiesExpectation("should offer help");
    }

    @Test
    void shouldHandleNullActualValue() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.0, \"reasoning\": \"Value is null.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel).withThreshold(0.0));

      // when / then - should not throw; the null value is passed to the LLM
      EvaluationAssertions.assertThat(null).satisfiesExpectation("should be something");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenLlmReturnsMalformedResponse() {
      // given
      final ChatModelAdapter mockModel = prompt -> "this is not valid json at all";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then
      Assertions.assertThatThrownBy(
              () ->
                  EvaluationAssertions.assertThat("some value")
                      .satisfiesExpectation("some expectation"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("Judge evaluation failed")
          .hasMessageContaining("unparseable response")
          .hasMessageContaining("this is not valid json at all");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldPropagateExceptionWhenLlmCallThrows() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> {
            throw new RuntimeException("Connection refused");
          };
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then
      Assertions.assertThatThrownBy(
              () ->
                  EvaluationAssertions.assertThat("some value")
                      .satisfiesExpectation("some expectation"))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Connection refused");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldThrowWhenExpectationIsNullOrBlank(final String expectation) {
      // given
      final ChatModelAdapter mockModel = prompt -> "{\"score\": 1.0, \"reasoning\": \"ok\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then
      assertThatThrownBy(
              () -> EvaluationAssertions.assertThat("some value").satisfiesExpectation(expectation))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expectation must not be null or empty");
    }

    @Test
    void shouldThrowWhenJudgeConfigNotSet() {
      // given - no judge config set

      // when / then
      assertThatThrownBy(
              () ->
                  EvaluationAssertions.assertThat("some value")
                      .satisfiesExpectation("some expectation"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("JudgeConfig is not set");
    }

    @Test
    void shouldThrowWhenChatModelNotConfigured() {
      // given — config with no chat model
      CamundaAssert.setJudgeConfig(JudgeConfig.defaults());

      // when / then
      assertThatThrownBy(
              () ->
                  EvaluationAssertions.assertThat("some value")
                      .satisfiesExpectation("some expectation"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ChatModelAdapter");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldUseConfiguredDefaultThreshold() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.7, \"reasoning\": \"Good but not great.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel).withThreshold(0.8));

      // when / then - score 0.7 is below configured threshold 0.8
      Assertions.assertThatThrownBy(
              () ->
                  EvaluationAssertions.assertThat("some value")
                      .satisfiesExpectation("some expectation"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("Score: 0.70")
          .hasMessageContaining("threshold: 0.80");
    }
  }

  @Nested
  class CustomPrompt {

    @Test
    void shouldPassCustomPromptToLlm() {
      // given
      final AtomicReference<String> capturedPrompt = new AtomicReference<>();
      final ChatModelAdapter mockModel =
          prompt -> {
            capturedPrompt.set(prompt);
            return "{\"score\": 1.0, \"reasoning\": \"match\"}";
          };
      CamundaAssert.setJudgeConfig(
          JudgeConfig.of(mockModel).withCustomPrompt("You are a domain-specific evaluator."));

      // when
      EvaluationAssertions.assertThat("Hello").satisfiesExpectation("should be a greeting");

      // then
      Assertions.assertThat(capturedPrompt.get())
          .startsWith("You are a domain-specific evaluator.")
          .doesNotContain("You are an impartial judge")
          .contains("<expectation>\nshould be a greeting\n</expectation>")
          .contains("<actual_value>\nHello\n</actual_value>");
    }

    @Test
    void shouldUseDefaultPromptWhenNoCustomPromptConfigured() {
      // given
      final AtomicReference<String> capturedPrompt = new AtomicReference<>();
      final ChatModelAdapter mockModel =
          prompt -> {
            capturedPrompt.set(prompt);
            return "{\"score\": 1.0, \"reasoning\": \"match\"}";
          };
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when
      EvaluationAssertions.assertThat("Hello").satisfiesExpectation("should be a greeting");

      // then
      Assertions.assertThat(capturedPrompt.get())
          .startsWith("You are an impartial judge")
          .contains("<expectation>\nshould be a greeting\n</expectation>")
          .contains("<actual_value>\nHello\n</actual_value>");
    }
  }

  @Nested
  class WithJudgeConfig {

    @Test
    void shouldUseOverriddenJudgeConfig() {
      // given — global judge returns low score, override replaces chat model
      final ChatModelAdapter globalModel =
          prompt -> "{\"score\": 0.1, \"reasoning\": \"Global judge.\"}";
      final ChatModelAdapter overrideModel =
          prompt -> "{\"score\": 0.9, \"reasoning\": \"Override judge.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(globalModel));

      // when / then — override judge passes (0.9 >= 0.5)
      EvaluationAssertions.assertThat("Hello")
          .withJudgeConfig(config -> config.withChatModelAdapter(overrideModel))
          .satisfiesExpectation("should be a greeting");
    }

    @Test
    void shouldSwitchBetweenJudgesInSameChain() {
      // given
      final AtomicBoolean judgeACalled = new AtomicBoolean(false);
      final AtomicBoolean judgeBCalled = new AtomicBoolean(false);

      final ChatModelAdapter judgeA =
          prompt -> {
            judgeACalled.set(true);
            return "{\"score\": 0.9, \"reasoning\": \"Judge A.\"}";
          };
      final ChatModelAdapter judgeB =
          prompt -> {
            judgeBCalled.set(true);
            return "{\"score\": 0.9, \"reasoning\": \"Judge B.\"}";
          };

      // when
      EvaluationAssertions.assertThat("Hello")
          .withJudgeConfig(config -> config.withChatModelAdapter(judgeA))
          .satisfiesExpectation("expectation A")
          .withJudgeConfig(config -> config.withChatModelAdapter(judgeB))
          .satisfiesExpectation("expectation B");

      // then
      Assertions.assertThat(judgeACalled).isTrue();
      Assertions.assertThat(judgeBCalled).isTrue();
    }

    @Test
    void shouldThrowWhenModifierIsNull() {
      // given
      final ChatModelAdapter mockModel = prompt -> "{\"score\": 1.0, \"reasoning\": \"ok\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then
      assertThatThrownBy(() -> EvaluationAssertions.assertThat("value").withJudgeConfig(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("modifier must not be null");
    }

    @Test
    void shouldModifyThresholdViaOverride() {
      // given — score 0.3 would fail default threshold (0.5) but pass custom threshold (0.2)
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.3, \"reasoning\": \"Low match.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then — should pass with lowered threshold
      EvaluationAssertions.assertThat("some text")
          .withJudgeConfig(config -> config.withThreshold(0.2))
          .satisfiesExpectation("some expectation");
    }

    @Test
    void shouldOverwriteOnMultipleCalls() {
      // given
      final AtomicReference<String> capturedPrompt = new AtomicReference<>();
      final ChatModelAdapter mockModel =
          prompt -> {
            capturedPrompt.set(prompt);
            return "{\"score\": 0.9, \"reasoning\": \"match\"}";
          };
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when — second withJudgeConfig overwrites the first
      EvaluationAssertions.assertThat("Hello")
          .withJudgeConfig(config -> config.withCustomPrompt("First prompt"))
          .withJudgeConfig(config -> config.withCustomPrompt("Second prompt"))
          .satisfiesExpectation("some expectation");

      // then
      Assertions.assertThat(capturedPrompt.get()).startsWith("Second prompt");
    }

    @Test
    void shouldNotAffectGlobalConfig() {
      // given
      final ChatModelAdapter mockModel = prompt -> "{\"score\": 0.9, \"reasoning\": \"match\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel).withThreshold(0.5));

      // when — modify threshold locally
      EvaluationAssertions.assertThat("Hello")
          .withJudgeConfig(config -> config.withThreshold(0.8))
          .satisfiesExpectation("some expectation");

      // then — global config unchanged
      Assertions.assertThat(CamundaAssert.getJudgeConfig().getThreshold()).isEqualTo(0.5);
    }

    @Test
    void shouldCreateBlankConfigWhenNoGlobalConfigSet() {
      // given — no global judge config, set up everything inline
      final ChatModelAdapter mockModel = prompt -> "{\"score\": 0.9, \"reasoning\": \"match\"}";

      // when / then — withJudgeConfig creates a blank default, withChatModelAdapter sets the model
      EvaluationAssertions.assertThat("Hello")
          .withJudgeConfig(config -> config.withChatModelAdapter(mockModel))
          .satisfiesExpectation("should be a greeting");
    }

    @Test
    void shouldThrowWhenBlankConfigUsedWithoutChatModel() {
      // given — no global config, withJudgeConfig only modifies threshold (no chat model set)

      // when / then
      assertThatThrownBy(
              () ->
                  EvaluationAssertions.assertThat("value")
                      .withJudgeConfig(config -> config.withThreshold(0.8))
                      .satisfiesExpectation("some expectation"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ChatModelAdapter");
    }

    @Test
    void shouldNotAffectGlobalDefaultForNewAssertThatCalls() {
      // given
      final AtomicBoolean globalCalled = new AtomicBoolean(false);

      final ChatModelAdapter globalModel =
          prompt -> {
            globalCalled.set(true);
            return "{\"score\": 0.9, \"reasoning\": \"Global judge.\"}";
          };
      final ChatModelAdapter overrideModel =
          prompt -> "{\"score\": 0.9, \"reasoning\": \"Override judge.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(globalModel));

      // when — use override on first assertion
      EvaluationAssertions.assertThat("Hello")
          .withJudgeConfig(config -> JudgeConfig.of(overrideModel))
          .satisfiesExpectation("some expectation");

      // then — new assertThat uses global default
      globalCalled.set(false);
      EvaluationAssertions.assertThat("Hello").satisfiesExpectation("some expectation");

      Assertions.assertThat(globalCalled).isTrue();
    }
  }
}
