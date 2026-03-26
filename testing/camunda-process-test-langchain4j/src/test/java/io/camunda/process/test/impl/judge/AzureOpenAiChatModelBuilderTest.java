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

class AzureOpenAiChatModelBuilderTest {

  @Test
  void shouldBuildChatModelWithApiKey() {
    // given
    final BaseProviderConfig.AzureOpenAiConfig config =
        new BaseProviderConfig.AzureOpenAiConfig(
            "gpt-4o", "https://my-resource.openai.azure.com/", "test-api-key");

    // when
    final ChatModel chatModel = AzureOpenAiChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithDefaultCredentials() {
    // given — no API key, falls back to DefaultAzureCredential
    final BaseProviderConfig.AzureOpenAiConfig config =
        new BaseProviderConfig.AzureOpenAiConfig(
            "gpt-4o", "https://my-resource.openai.azure.com/", null);

    // when
    final ChatModel chatModel = AzureOpenAiChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithTimeout() {
    // given
    final BaseProviderConfig.AzureOpenAiConfig config =
        new BaseProviderConfig.AzureOpenAiConfig(
            "gpt-4o", "https://my-resource.openai.azure.com/", "test-api-key");
    config.setTimeout(Duration.ofSeconds(45));

    // when
    final ChatModel chatModel = AzureOpenAiChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithTemperature() {
    // given
    final BaseProviderConfig.AzureOpenAiConfig config =
        new BaseProviderConfig.AzureOpenAiConfig(
            "gpt-4o", "https://my-resource.openai.azure.com/", "test-api-key");
    config.setTemperature(0.7);

    // when
    final ChatModel chatModel = AzureOpenAiChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final BaseProviderConfig.AzureOpenAiConfig config =
        new BaseProviderConfig.AzureOpenAiConfig(
            model, "https://my-resource.openai.azure.com/", "test-api-key");

    // when / then
    assertThatThrownBy(() -> AzureOpenAiChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("azure-openai");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenEndpointMissingOrBlank(final String endpoint) {
    // given
    final BaseProviderConfig.AzureOpenAiConfig config =
        new BaseProviderConfig.AzureOpenAiConfig("gpt-4o", endpoint, "test-api-key");

    // when / then
    assertThatThrownBy(() -> AzureOpenAiChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("endpoint")
        .hasMessageContaining("azure-openai");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void shouldFallbackToDefaultCredentialsWhenApiKeyBlank(final String apiKey) {
    // given — blank API key is treated as absent
    final BaseProviderConfig.AzureOpenAiConfig config =
        new BaseProviderConfig.AzureOpenAiConfig(
            "gpt-4o", "https://my-resource.openai.azure.com/", apiKey);

    // when
    final ChatModel chatModel = AzureOpenAiChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }
}
