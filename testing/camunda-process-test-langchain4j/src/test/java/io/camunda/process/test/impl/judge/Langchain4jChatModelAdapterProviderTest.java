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

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.ChatModelAdapterProvider;
import io.camunda.process.test.api.judge.ProviderConfig;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class Langchain4jChatModelAdapterProviderTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("providerConfigurations")
  void shouldCreateAdapterForKnownProvider(
      final String providerName,
      final ChatModelAdapterProvider provider,
      final ProviderConfig config) {
    // when
    final ChatModelAdapter adapter = provider.create(config);

    // then
    assertThat(adapter).isNotNull();
  }

  static Stream<Arguments> providerConfigurations() {
    return Stream.of(
        Arguments.of(
            "openai",
            new OpenAiChatModelAdapterProvider(),
            new BaseProviderConfig.OpenAiConfig("gpt-4o", "test-key")),
        Arguments.of(
            "anthropic",
            new AnthropicChatModelAdapterProvider(),
            new BaseProviderConfig.AnthropicConfig("claude-3-5-sonnet-20241022", "test-key")),
        Arguments.of(
            "amazon-bedrock",
            new BedrockChatModelAdapterProvider(),
            new BaseProviderConfig.AmazonBedrockConfig(
                "anthropic.claude-3-5-sonnet-20241022-v2:0",
                "us-east-1",
                null,
                "test-access-key",
                "test-secret-key")),
        Arguments.of(
            "openai-compatible",
            new OpenAiCompatibleChatModelAdapterProvider(),
            new BaseProviderConfig.OpenAiCompatibleConfig(
                "mistral-7b",
                "http://localhost:11434/v1",
                null,
                Map.of("Authorization", "Bearer xyz"))),
        Arguments.of(
            "azure-openai",
            new AzureOpenAiChatModelAdapterProvider(),
            new BaseProviderConfig.AzureOpenAiConfig(
                "gpt-4o", "https://my-resource.openai.azure.com/", "test-key")));
  }
}
