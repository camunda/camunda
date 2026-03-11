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

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.process.test.api.judge.JudgeConfigBootstrapData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AnthropicChatModelBuilderTest {

  @Test
  void shouldBuildChatModel() {
    // given
    final JudgeConfigBootstrapData.AnthropicConfig config =
        new JudgeConfigBootstrapData.AnthropicConfig("claude-3-5-sonnet-20241022", "test-api-key");

    // when
    final ChatModel chatModel = AnthropicChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenApiKeyMissingOrBlank(final String apiKey) {
    // given
    final JudgeConfigBootstrapData.AnthropicConfig config =
        new JudgeConfigBootstrapData.AnthropicConfig("claude-3-5-sonnet-20241022", apiKey);

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
    final JudgeConfigBootstrapData.AnthropicConfig config =
        new JudgeConfigBootstrapData.AnthropicConfig(model, "test-api-key");

    // when / then
    assertThatThrownBy(() -> AnthropicChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("anthropic");
  }
}
