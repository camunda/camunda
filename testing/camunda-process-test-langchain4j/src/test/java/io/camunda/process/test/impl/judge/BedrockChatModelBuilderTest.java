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

class BedrockChatModelBuilderTest {

  // -- Happy paths --

  @Test
  void shouldBuildChatModelWithAccessKeyCredentials() {
    // given
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "us-east-1",
            null,
            "test-access-key",
            "test-secret-key");

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithApiKey() {
    // given
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0", "us-east-1", "test-api-key", null, null);

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithDefaultCredentialChain() {
    // given — no explicit authentication, uses AWS default credential chain
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0", "us-east-1", null, null, null);

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithTimeout() {
    // given
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "us-east-1",
            null,
            "test-access-key",
            "test-secret-key");
    config.setTimeout(Duration.ofSeconds(60));

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithTemperature() {
    // given
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "us-east-1",
            null,
            "test-access-key",
            "test-secret-key");
    config.setTemperature(0.7);

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  // -- Required field validation --

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            model, "us-east-1", null, "test-access-key", "test-secret-key");

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("amazon-bedrock");
  }

  // -- Blank optional fields treated as absent --

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void shouldFallbackToDefaultCredentialChainWhenApiKeyBlank(final String apiKey) {
    // given — blank apiKey is treated as absent
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0", "us-east-1", apiKey, null, null);

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void shouldFallbackToDefaultCredentialChainWhenBothKeysBlank(final String blankValue) {
    // given — both blank credentials are treated as absent
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0", "us-east-1", null, blankValue, blankValue);

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  // -- Authentication edge cases --

  @Test
  void shouldThrowWhenBothAuthMethodsProvided() {
    // given — both accessKey/secretKey and apiKey are set
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "us-east-1",
            "test-api-key",
            "test-access-key",
            "test-secret-key");

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("amazon-bedrock");
  }

  @Test
  void shouldThrowWhenOnlyAccessKeyProvided() {
    // given — only accessKey without secretKey
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "us-east-1",
            null,
            "test-access-key",
            null);

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accessKey")
        .hasMessageContaining("secretKey");
  }

  @Test
  void shouldThrowWhenOnlySecretKeyProvided() {
    // given — only secretKey without accessKey
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "us-east-1",
            null,
            null,
            "test-secret-key");

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accessKey")
        .hasMessageContaining("secretKey");
  }

  @Test
  void shouldThrowWhenAccessKeyBlankAndSecretKeySet() {
    // given — blank accessKey treated as absent, creating a partial key-pair
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "us-east-1",
            null,
            "  ",
            "test-secret-key");

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accessKey")
        .hasMessageContaining("secretKey");
  }

  @Test
  void shouldThrowWhenSecretKeyBlankAndAccessKeySet() {
    // given — blank secretKey treated as absent, creating a partial key-pair
    final BaseProviderConfig.AmazonBedrockConfig config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "anthropic.claude-3-5-sonnet-20241022-v2:0", "us-east-1", null, "test-access-key", "");

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accessKey")
        .hasMessageContaining("secretKey");
  }
}
