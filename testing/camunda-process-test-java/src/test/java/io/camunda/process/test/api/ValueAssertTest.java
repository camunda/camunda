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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
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

public class ValueAssertTest {

  private static final float[] UNIT_VEC_X = {1.0f, 0.0f};
  private static final float[] UNIT_VEC_Y = {0.0f, 1.0f};

  @AfterEach
  void resetConfigs() {
    CamundaAssert.setJudgeConfig(null);
    CamundaAssert.setSemanticSimilarityConfig(null);
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
      CamundaAssert.assertThatValue("Hello, World!").satisfiesJudge("should be a greeting");
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
                  CamundaAssert.assertThatValue("random text")
                      .satisfiesJudge("should be an email address"))
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
                  CamundaAssert.assertThatValue("the actual text value")
                      .satisfiesJudge("should be something else"))
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
      CamundaAssert.assertThatValue("borderline value").satisfiesJudge("some expectation");
    }

    @Test
    void shouldPassWithCustomThreshold() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.3, \"reasoning\": \"Low score.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then - should pass with low threshold
      CamundaAssert.assertThatValue("some text")
          .withJudgeConfig(config -> config.withThreshold(0.2))
          .satisfiesJudge("some expectation");
    }

    @Test
    void shouldSupportChainingMultipleExpectations() {
      // given
      final ChatModelAdapter mockModel = prompt -> "{\"score\": 0.9, \"reasoning\": \"Matches.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then - chaining should work
      CamundaAssert.assertThatValue("Hello, how can I help you today?")
          .satisfiesJudge("should be a greeting")
          .satisfiesJudge("should offer help");
    }

    @Test
    void shouldHandleNullActualValue() {
      // given
      final ChatModelAdapter mockModel =
          prompt -> "{\"score\": 0.0, \"reasoning\": \"Value is null.\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel).withThreshold(0.0));

      // when / then - should not throw; the null value is passed to the LLM
      CamundaAssert.assertThatValue(null).satisfiesJudge("should be something");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenLlmReturnsMalformedResponse() {
      // given
      final ChatModelAdapter mockModel = prompt -> "this is not valid json at all";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel));

      // when / then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThatValue("some value").satisfiesJudge("some expectation"))
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
              () -> CamundaAssert.assertThatValue("some value").satisfiesJudge("some expectation"))
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
              () -> CamundaAssert.assertThatValue("some value").satisfiesJudge(expectation))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expectation must not be null or empty");
    }

    @Test
    void shouldThrowWhenJudgeConfigNotSet() {
      // given - no judge config set

      // when / then
      assertThatThrownBy(
              () -> CamundaAssert.assertThatValue("some value").satisfiesJudge("some expectation"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("JudgeConfig is not set");
    }

    @Test
    void shouldThrowWhenChatModelNotConfigured() {
      // given — config with no chat model
      CamundaAssert.setJudgeConfig(JudgeConfig.defaults());

      // when / then
      assertThatThrownBy(
              () -> CamundaAssert.assertThatValue("some value").satisfiesJudge("some expectation"))
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
              () -> CamundaAssert.assertThatValue("some value").satisfiesJudge("some expectation"))
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
      CamundaAssert.assertThatValue("Hello").satisfiesJudge("should be a greeting");

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
      CamundaAssert.assertThatValue("Hello").satisfiesJudge("should be a greeting");

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
      CamundaAssert.assertThatValue("Hello")
          .withJudgeConfig(config -> config.withChatModelAdapter(overrideModel))
          .satisfiesJudge("should be a greeting");
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
      CamundaAssert.assertThatValue("Hello")
          .withJudgeConfig(config -> config.withChatModelAdapter(judgeA))
          .satisfiesJudge("expectation A")
          .withJudgeConfig(config -> config.withChatModelAdapter(judgeB))
          .satisfiesJudge("expectation B");

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
      assertThatThrownBy(() -> CamundaAssert.assertThatValue("value").withJudgeConfig(null))
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
      CamundaAssert.assertThatValue("some text")
          .withJudgeConfig(config -> config.withThreshold(0.2))
          .satisfiesJudge("some expectation");
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
      CamundaAssert.assertThatValue("Hello")
          .withJudgeConfig(config -> config.withCustomPrompt("First prompt"))
          .withJudgeConfig(config -> config.withCustomPrompt("Second prompt"))
          .satisfiesJudge("some expectation");

      // then
      Assertions.assertThat(capturedPrompt.get()).startsWith("Second prompt");
    }

    @Test
    void shouldNotAffectGlobalConfig() {
      // given
      final ChatModelAdapter mockModel = prompt -> "{\"score\": 0.9, \"reasoning\": \"match\"}";
      CamundaAssert.setJudgeConfig(JudgeConfig.of(mockModel).withThreshold(0.5));

      // when — modify threshold locally
      CamundaAssert.assertThatValue("Hello")
          .withJudgeConfig(config -> config.withThreshold(0.8))
          .satisfiesJudge("some expectation");

      // then — global config unchanged
      Assertions.assertThat(CamundaAssert.getJudgeConfig().getThreshold()).isEqualTo(0.5);
    }

    @Test
    void shouldCreateBlankConfigWhenNoGlobalConfigSet() {
      // given — no global judge config, set up everything inline
      final ChatModelAdapter mockModel = prompt -> "{\"score\": 0.9, \"reasoning\": \"match\"}";

      // when / then — withJudgeConfig creates a blank default, withChatModelAdapter sets the model
      CamundaAssert.assertThatValue("Hello")
          .withJudgeConfig(config -> config.withChatModelAdapter(mockModel))
          .satisfiesJudge("should be a greeting");
    }

    @Test
    void shouldThrowWhenBlankConfigUsedWithoutChatModel() {
      // given — no global config, withJudgeConfig only modifies threshold (no chat model set)

      // when / then
      assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatValue("value")
                      .withJudgeConfig(config -> config.withThreshold(0.8))
                      .satisfiesJudge("some expectation"))
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
      CamundaAssert.assertThatValue("Hello")
          .withJudgeConfig(config -> JudgeConfig.of(overrideModel))
          .satisfiesJudge("some expectation");

      // then — new assertThat uses global default
      globalCalled.set(false);
      CamundaAssert.assertThatValue("Hello").satisfiesJudge("some expectation");

      Assertions.assertThat(globalCalled).isTrue();
    }
  }

  @Nested
  class IsSimilarTo {

    @Test
    void shouldPassWhenSimilarityScoreAboveThreshold() {
      // given — identical vectors → cosine similarity == 1.0
      final EmbeddingModelAdapter model = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      // when / then — should not throw
      CamundaAssert.assertThatValue("Hello, World!").isSimilarTo("Hello, World!");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenSimilarityScoreBelowThreshold() {
      // given — orthogonal vectors → cosine similarity == 0.0
      final EmbeddingModelAdapter model =
          text -> text.equals("expected text") ? UNIT_VEC_X : UNIT_VEC_Y;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      // when / then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThatValue("unrelated text").isSimilarTo("expected text"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("did not satisfy similarity check")
          .hasMessageContaining("Score: 0.00")
          .hasMessageContaining("threshold: 0.50");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldIncludeExpectationAndActualValueInFailureMessage() {
      // given — orthogonal vectors → score 0.0
      final EmbeddingModelAdapter model =
          text -> text.equals("the expected value") ? UNIT_VEC_X : UNIT_VEC_Y;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      // when / then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatValue("the actual text value")
                      .isSimilarTo("the expected value"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("the actual text value")
          .hasMessageContaining("the expected value")
          .hasMessageContaining("Score: 0.00")
          .hasMessageContaining("threshold: 0.50");
    }

    @Test
    void shouldPassWhenScoreExactlyAtThreshold() {
      // given — identical vectors → score == 1.0; set threshold == 1.0
      final EmbeddingModelAdapter model = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(model).withThreshold(1.0));

      // when / then — score == threshold should pass
      CamundaAssert.assertThatValue("some text").isSimilarTo("some text");
    }

    @Test
    void shouldSupportChainingMultipleIsSimilarToAssertions() {
      // given — identical vectors → score == 1.0
      final EmbeddingModelAdapter model = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      // when / then — chaining should work
      CamundaAssert.assertThatValue("some text")
          .isSimilarTo("expectation A")
          .isSimilarTo("expectation B");
    }

    @Test
    void shouldHandleNullActualValue() {
      // given — identical vectors → score == 1.0
      final EmbeddingModelAdapter model = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      // when / then - should not throw; the null value is passed to the embedding model
      CamundaAssert.assertThatValue("some text")
          .isSimilarTo("expectation A")
          .isSimilarTo("expectation B");
    }

    @Test
    void shouldThrowWhenSemanticSimilarityConfigNotSet() {
      // given — no config set

      // when / then
      assertThatThrownBy(() -> CamundaAssert.assertThatValue("some value").isSimilarTo("expected"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SemanticSimilarityConfig is not set");
    }

    @Test
    void shouldThrowWhenEmbeddingModelNotConfigured() {
      // given — config with no embedding model
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.defaults());

      // when / then
      assertThatThrownBy(() -> CamundaAssert.assertThatValue("some value").isSimilarTo("expected"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("EmbeddingModelAdapter");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldThrowWhenExpectedValueIsNullOrBlank(final String expectedValue) {
      // given
      final EmbeddingModelAdapter model = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      // when / then
      assertThatThrownBy(
              () -> CamundaAssert.assertThatValue("some value").isSimilarTo(expectedValue))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expectation must not be null or empty");
    }
  }

  @Nested
  class WithSemanticSimilarityConfig {

    @Test
    void shouldUseOverriddenConfig() {
      // given — global model returns orthogonal vector (would fail), override uses identical vector
      final EmbeddingModelAdapter globalModel = text -> UNIT_VEC_Y;
      final EmbeddingModelAdapter overrideModel = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(globalModel));

      // when / then — override model passes (score == 1.0)
      CamundaAssert.assertThatValue("Hello")
          .withSemanticSimilarityConfig(
              c -> SemanticSimilarityConfig.defaults().withEmbeddingModelAdapter(overrideModel))
          .isSimilarTo("Hello");
    }

    @Test
    void shouldModifyThresholdViaOverride() {
      // given — orthogonal vectors → score 0.0; global threshold 0.5 would fail, local 0.0 passes
      final EmbeddingModelAdapter model = text -> text.equals("expected") ? UNIT_VEC_X : UNIT_VEC_Y;
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(model).withThreshold(0.5));

      // when / then — locally lower threshold to 0.0; if global threshold (0.5) were accidentally
      // used instead, the assertion would fail since score (0.0) < 0.5
      CamundaAssert.assertThatValue("actual")
          .withSemanticSimilarityConfig(c -> c.withThreshold(0.0))
          .isSimilarTo("expected");
    }

    @Test
    void shouldNotAffectGlobalConfig() {
      // given — orthogonal vectors → score 0.0; global threshold 0.5 would fail, local 0.0 passes
      final EmbeddingModelAdapter model = text -> text.equals("expected") ? UNIT_VEC_X : UNIT_VEC_Y;
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(model).withThreshold(0.5));

      // when — locally lower the threshold so the score 0.0 passes
      CamundaAssert.assertThatValue("actual")
          .withSemanticSimilarityConfig(c -> c.withThreshold(0.0))
          .isSimilarTo("expected");

      // then — global config unchanged
      Assertions.assertThat(CamundaAssert.getSemanticSimilarityConfig().getThreshold())
          .isEqualTo(0.5);
    }

    @Test
    void shouldCreateBlankConfigWhenNoGlobalConfigSet() {
      // given — no global config, set up everything inline
      final EmbeddingModelAdapter model = text -> UNIT_VEC_X;

      // when / then — withSemanticSimilarityConfig creates a blank default, sets model inline
      CamundaAssert.assertThatValue("Hello")
          .withSemanticSimilarityConfig(c -> c.withEmbeddingModelAdapter(model))
          .isSimilarTo("Hello");
    }

    @Test
    void shouldThrowWhenModifierIsNull() {
      // given
      final EmbeddingModelAdapter model = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      // when / then
      assertThatThrownBy(
              () -> CamundaAssert.assertThatValue("value").withSemanticSimilarityConfig(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("modifier must not be null");
    }

    @Test
    void shouldThrowWhenModifierReturnsNull() {
      // given
      final EmbeddingModelAdapter model = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      // when / then
      assertThatThrownBy(
              () -> CamundaAssert.assertThatValue("value").withSemanticSimilarityConfig(c -> null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("modifier must not return null");
    }

    @Test
    void shouldSwitchBetweenConfigsInSameChain() {
      // given
      final EmbeddingModelAdapter modelA = mock(EmbeddingModelAdapter.class);
      final EmbeddingModelAdapter modelB = mock(EmbeddingModelAdapter.class);
      when(modelA.embed(anyString())).thenReturn(UNIT_VEC_X);
      when(modelB.embed(anyString())).thenReturn(UNIT_VEC_X);

      // when
      CamundaAssert.assertThatValue("Hello")
          .withSemanticSimilarityConfig(c -> SemanticSimilarityConfig.of(modelA))
          .isSimilarTo("expectation A")
          .withSemanticSimilarityConfig(c -> SemanticSimilarityConfig.of(modelB))
          .isSimilarTo("expectation B");

      // then
      verify(modelA, atLeastOnce()).embed(anyString());
      verify(modelB, atLeastOnce()).embed(anyString());
    }
  }
}
