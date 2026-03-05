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

class OpenAiCompatibleChatModelBuilderTest {

  @Test
  void shouldBuildChatModelWithApiKey() {
    // given
    final JudgeConfigurationData data =
        JudgeConfigurationData.builder()
            .baseUrl("http://localhost:11434/v1")
            .model("llama3")
            .apiKey("test-api-key")
            .build();

    // when
    final ChatModel chatModel = OpenAiCompatibleChatModelBuilder.build(data);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldBuildChatModelWithoutApiKey() {
    // given
    final JudgeConfigurationData data =
        JudgeConfigurationData.builder()
            .baseUrl("http://localhost:11434/v1")
            .model("llama3")
            .build();

    // when
    final ChatModel chatModel = OpenAiCompatibleChatModelBuilder.build(data);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldThrowWhenBaseUrlMissing() {
    // given
    final JudgeConfigurationData data = JudgeConfigurationData.builder().model("llama3").build();

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("baseUrl")
        .hasMessageContaining("openai-compatible");
  }

  @Test
  void shouldThrowWhenModelMissing() {
    // given
    final JudgeConfigurationData data =
        JudgeConfigurationData.builder().baseUrl("http://localhost:11434/v1").build();

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("openai-compatible");
  }

  @Test
  void shouldThrowWhenBaseUrlBlank() {
    // given
    final JudgeConfigurationData data =
        JudgeConfigurationData.builder().baseUrl("  ").model("llama3").build();

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("baseUrl");
  }

  @Test
  void shouldThrowWhenModelBlank() {
    // given
    final JudgeConfigurationData data =
        JudgeConfigurationData.builder().baseUrl("http://localhost:11434/v1").model("").build();

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleChatModelBuilder.build(data))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model");
  }
}
