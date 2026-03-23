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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiCompatibleChatModelBuilderTest {

  @Test
  void shouldBuildChatModelWithApiKey() {
    // given
    final BaseProviderConfig.OpenAiCompatibleConfig config =
        new BaseProviderConfig.OpenAiCompatibleConfig(
            "llama3", "http://localhost:11434/v1", "test-api-key", null);

    // when
    final ChatModel chatModel = OpenAiCompatibleChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithHeaders() {
    // given
    final BaseProviderConfig.OpenAiCompatibleConfig config =
        new BaseProviderConfig.OpenAiCompatibleConfig(
            "llama3",
            "http://localhost:11434/v1",
            null,
            Map.of("X-Test-Header", "test-header-value"));

    // when
    final ChatModel chatModel = OpenAiCompatibleChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWhenBothAuthorizationHeaderAndApiKeyPresent() {
    // given
    final BaseProviderConfig.OpenAiCompatibleConfig config =
        new BaseProviderConfig.OpenAiCompatibleConfig(
            "llama3",
            "http://localhost:11434/v1",
            "test-api-key",
            Map.of("Authorization", "Bearer test-token"));

    // when
    final ChatModel chatModel = OpenAiCompatibleChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithTimeout() {
    // given
    final BaseProviderConfig.OpenAiCompatibleConfig config =
        new BaseProviderConfig.OpenAiCompatibleConfig(
            "llama3", "http://localhost:11434/v1", "test-api-key", null);
    config.setTimeout(Duration.ofSeconds(30));

    // when
    final ChatModel chatModel = OpenAiCompatibleChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithTemperature() {
    // given
    final BaseProviderConfig.OpenAiCompatibleConfig config =
        new BaseProviderConfig.OpenAiCompatibleConfig(
            "llama3", "http://localhost:11434/v1", "test-api-key", null);
    config.setTemperature(0.7);

    // when
    final ChatModel chatModel = OpenAiCompatibleChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldBuildChatModelWithoutApiKey(final String apiKey) {
    // given — null or blank apiKey is treated as absent
    final BaseProviderConfig.OpenAiCompatibleConfig config =
        new BaseProviderConfig.OpenAiCompatibleConfig(
            "llama3", "http://localhost:11434/v1", apiKey, null);

    // when
    final ChatModel chatModel = OpenAiCompatibleChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenBaseUrlMissingOrBlank(final String baseUrl) {
    // given
    final BaseProviderConfig.OpenAiCompatibleConfig config =
        new BaseProviderConfig.OpenAiCompatibleConfig("llama3", baseUrl, null, null);

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("baseUrl")
        .hasMessageContaining("openai-compatible");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final BaseProviderConfig.OpenAiCompatibleConfig config =
        new BaseProviderConfig.OpenAiCompatibleConfig(
            model, "http://localhost:11434/v1", null, null);

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("openai-compatible");
  }
}
