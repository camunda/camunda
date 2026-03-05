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

  @Test
  void shouldThrowWhenModelMissing() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
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

  @Test
  void shouldThrowWhenModelBlank() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder().model("  ").region("us-east-1").build();

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model");
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
}
