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
package io.camunda.process.test.impl.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.process.test.impl.judge.BaseProviderConfig.AnthropicConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AnthropicChatModelBuilderTest {

  private static final String MODEL = "claude-3-5-sonnet-20241022";
  private static final String API_KEY = "test-api-key";

  @Test
  void shouldBuildChatModel() {
    // given
    final AnthropicConfig config = new AnthropicConfig(MODEL, API_KEY);

    // when
    final ChatModel chatModel = AnthropicChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldSetRequiredFieldsOnBuilder() {
    // given
    final AnthropicConfig config = new AnthropicConfig(MODEL, API_KEY);
    final AnthropicChatModel.AnthropicChatModelBuilder mockBuilder =
        mock(AnthropicChatModel.AnthropicChatModelBuilder.class);

    // when
    AnthropicChatModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).apiKey(API_KEY);
    verify(mockBuilder).modelName(MODEL);
  }

  @Test
  void shouldApplyTimeoutToBuilder() {
    // given
    final AnthropicConfig config = new AnthropicConfig(MODEL, API_KEY);
    config.setTimeout(Duration.ofSeconds(30));
    final AnthropicChatModel.AnthropicChatModelBuilder mockBuilder =
        mock(AnthropicChatModel.AnthropicChatModelBuilder.class);

    // when
    AnthropicChatModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).timeout(Duration.ofSeconds(30));
    verify(mockBuilder, never()).temperature(any());
  }

  @Test
  void shouldApplyTemperatureToBuilder() {
    // given
    final AnthropicConfig config = new AnthropicConfig(MODEL, API_KEY);
    config.setTemperature(0.7);
    final AnthropicChatModel.AnthropicChatModelBuilder mockBuilder =
        mock(AnthropicChatModel.AnthropicChatModelBuilder.class);

    // when
    AnthropicChatModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder, never()).timeout(any());
    verify(mockBuilder).temperature(0.7);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenApiKeyMissingOrBlank(final String apiKey) {
    // given
    final AnthropicConfig config = new AnthropicConfig(MODEL, apiKey);

    // when / then
    assertThatThrownBy(() -> AnthropicChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apiKey")
        .hasMessageContaining("anthropic");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final AnthropicConfig config = new AnthropicConfig(model, API_KEY);

    // when / then
    assertThatThrownBy(() -> AnthropicChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("anthropic");
  }
}
