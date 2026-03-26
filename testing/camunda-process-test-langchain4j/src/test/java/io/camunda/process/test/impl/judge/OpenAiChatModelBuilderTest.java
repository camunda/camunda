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
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiChatModelBuilderTest {

  @Test
  void shouldBuildChatModel() {
    // given
    final BaseProviderConfig.OpenAiConfig config =
        new BaseProviderConfig.OpenAiConfig("gpt-4o", "test-api-key");

    // when
    final ChatModel chatModel = OpenAiChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenApiKeyMissingOrBlank(final String apiKey) {
    // given
    final BaseProviderConfig.OpenAiConfig config =
        new BaseProviderConfig.OpenAiConfig("gpt-4o", apiKey);

    // when / then
    assertThatThrownBy(() -> OpenAiChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apiKey")
        .hasMessageContaining("openai");
  }

  @Test
  void shouldBuildChatModelWithTimeout() {
    // given
    final BaseProviderConfig.OpenAiConfig config =
        new BaseProviderConfig.OpenAiConfig("gpt-4o", "test-api-key");
    config.setTimeout(Duration.ofSeconds(30));

    // when
    final ChatModel chatModel = OpenAiChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithTemperature() {
    // given
    final BaseProviderConfig.OpenAiConfig config =
        new BaseProviderConfig.OpenAiConfig("gpt-4o", "test-api-key");
    config.setTemperature(0.7);

    // when
    final ChatModel chatModel = OpenAiChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final BaseProviderConfig.OpenAiConfig config =
        new BaseProviderConfig.OpenAiConfig(model, "test-api-key");

    // when / then
    assertThatThrownBy(() -> OpenAiChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("openai");
  }
}
