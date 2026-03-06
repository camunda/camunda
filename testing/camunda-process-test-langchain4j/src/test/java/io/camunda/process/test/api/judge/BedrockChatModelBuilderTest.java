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
package io.camunda.process.test.api.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BedrockChatModelBuilderTest {

  // -- Happy paths --

  @Test
  void shouldBuildChatModelWithAccessKeyCredentials() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .credentialsAccessKey("test-access-key")
            .credentialsSecretKey("test-secret-key")
            .build();

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(data);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithApiKey() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .apiKey("test-api-key")
            .build();

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(data);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithDefaultCredentialChain() {
    // given — no explicit authentication, uses AWS default credential chain
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .build();

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(data);

    // then
    assertThat(chatModel).isNotNull();
  }

  // -- Required field validation --

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model(model)
            .region("us-east-1")
            .credentialsAccessKey("test-access-key")
            .credentialsSecretKey("test-secret-key")
            .build();

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("amazon-bedrock");
  }

  // -- Blank optional fields treated as absent --

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void shouldFallbackToDefaultCredentialChainWhenApiKeyBlank(final String apiKey) {
    // given — blank apiKey is treated as absent
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .apiKey(apiKey)
            .build();

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(data);

    // then
    assertThat(chatModel).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void shouldFallbackToDefaultCredentialChainWhenBothKeysBlank(final String blankValue) {
    // given — both blank credentials are treated as absent
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .credentialsAccessKey(blankValue)
            .credentialsSecretKey(blankValue)
            .build();

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(data);

    // then
    assertThat(chatModel).isNotNull();
  }

  // -- Authentication edge cases --

  @Test
  void shouldThrowWhenBothAuthMethodsProvided() {
    // given — both accessKey/secretKey and apiKey are set
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .credentialsAccessKey("test-access-key")
            .credentialsSecretKey("test-secret-key")
            .apiKey("test-api-key")
            .build();

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("amazon-bedrock");
  }

  @Test
  void shouldThrowWhenOnlyAccessKeyProvided() {
    // given — only accessKey without secretKey
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .credentialsAccessKey("test-access-key")
            .build();

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("credentialsAccessKey")
        .hasMessageContaining("credentialsSecretKey");
  }

  @Test
  void shouldThrowWhenOnlySecretKeyProvided() {
    // given — only secretKey without accessKey
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .credentialsSecretKey("test-secret-key")
            .build();

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("credentialsAccessKey")
        .hasMessageContaining("credentialsSecretKey");
  }

  @Test
  void shouldThrowWhenAccessKeyBlankAndSecretKeySet() {
    // given — blank accessKey treated as absent, creating a partial key-pair
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .credentialsAccessKey("  ")
            .credentialsSecretKey("test-secret-key")
            .build();

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("credentialsAccessKey")
        .hasMessageContaining("credentialsSecretKey");
  }

  @Test
  void shouldThrowWhenSecretKeyBlankAndAccessKeySet() {
    // given — blank secretKey treated as absent, creating a partial key-pair
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .model("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .region("us-east-1")
            .credentialsAccessKey("test-access-key")
            .credentialsSecretKey("")
            .build();

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("credentialsAccessKey")
        .hasMessageContaining("credentialsSecretKey");
  }
}
