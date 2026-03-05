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

class OpenAiChatModelBuilderTest {

  @Test
  void shouldBuildChatModel() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder().apiKey("test-api-key").model("gpt-4o").build();

    // when
    final ChatModel chatModel = OpenAiChatModelBuilder.build(data);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldThrowWhenApiKeyMissing() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder().model("gpt-4o").build();

    // when / then
    assertThatThrownBy(() -> OpenAiChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apiKey")
        .hasMessageContaining("openai");
  }

  @Test
  void shouldThrowWhenModelMissing() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder().apiKey("test-api-key").build();

    // when / then
    assertThatThrownBy(() -> OpenAiChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("openai");
  }

  @Test
  void shouldThrowWhenApiKeyBlank() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder().apiKey("  ").model("gpt-4o").build();

    // when / then
    assertThatThrownBy(() -> OpenAiChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apiKey");
  }

  @Test
  void shouldThrowWhenModelBlank() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder().apiKey("test-api-key").model("").build();

    // when / then
    assertThatThrownBy(() -> OpenAiChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model");
  }
}
