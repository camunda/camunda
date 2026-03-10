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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class JudgeConfigBootstrapTest {

  private final JudgeConfigBootstrapProvider bootstrap = new JudgeConfigBootstrap();

  @Test
  void shouldReturnNullWhenNotConfigured() {
    // given
    final JudgeConfigBootstrapData data = JudgeConfigBootstrapData.builder().build();

    // when
    final JudgeConfig config = bootstrap.bootstrap(data);

    // then
    assertThat(config).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providerConfigurations")
  void shouldBootstrapKnownProvider(
      final String providerName, final JudgeConfigBootstrapData data) {
    // when
    final JudgeConfig config = bootstrap.bootstrap(data);

    // then
    assertThat(config).isNotNull();
    assertThat(config.getChatModel()).isNotNull();
    assertThat(config.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
    assertThat(config.getCustomPrompt()).isNull();
  }

  static Stream<Arguments> providerConfigurations() {
    return Stream.of(
        Arguments.of("openai", openAiData()),
        Arguments.of("anthropic", anthropicData()),
        Arguments.of("amazon-bedrock", bedrockData()),
        Arguments.of("openai-compatible", openAiCompatibleData()));
  }

  @Test
  void shouldBootstrapWithCustomThresholdAndPrompt() {
    // given
    final JudgeConfigBootstrapData data =
        JudgeConfigBootstrapData.builder()
            .providerConfig(new JudgeConfigBootstrapData.OpenAiConfig("gpt-4o", "test-key"))
            .threshold(0.8)
            .customPrompt("Custom evaluation criteria")
            .build();

    // when
    final JudgeConfig config = bootstrap.bootstrap(data);

    // then
    assertThat(config).isNotNull();
    assertThat(config.getThreshold()).isEqualTo(0.8);
    assertThat(config.getCustomPrompt()).isEqualTo("Custom evaluation criteria");
  }

  private static JudgeConfigBootstrapData openAiData() {
    return JudgeConfigBootstrapData.builder()
        .providerConfig(new JudgeConfigBootstrapData.OpenAiConfig("gpt-4o", "test-key"))
        .build();
  }

  private static JudgeConfigBootstrapData anthropicData() {
    return JudgeConfigBootstrapData.builder()
        .providerConfig(
            new JudgeConfigBootstrapData.AnthropicConfig("claude-3-5-sonnet-20241022", "test-key"))
        .build();
  }

  private static JudgeConfigBootstrapData bedrockData() {
    return JudgeConfigBootstrapData.builder()
        .providerConfig(
            new JudgeConfigBootstrapData.AmazonBedrockConfig(
                "anthropic.claude-3-5-sonnet-20241022-v2:0",
                "us-east-1",
                null,
                "test-access-key",
                "test-secret-key"))
        .build();
  }

  private static JudgeConfigBootstrapData openAiCompatibleData() {
    return JudgeConfigBootstrapData.builder()
        .providerConfig(
            new JudgeConfigBootstrapData.OpenAiCompatibleConfig(
                "mistral-7b", "http://localhost:11434/v1", null))
        .build();
  }
}
