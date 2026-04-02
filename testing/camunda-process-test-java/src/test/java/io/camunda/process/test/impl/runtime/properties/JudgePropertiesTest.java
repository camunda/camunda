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
package io.camunda.process.test.impl.runtime.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.judge.ProviderConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.AzureOpenAiConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.OpenAiCompatibleConfig;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class JudgePropertiesTest {

  @Test
  void shouldReturnDefaults() {
    // given
    final Properties properties = new Properties();

    // when
    final JudgeProperties judgeProperties = new JudgeProperties(properties);

    // then
    assertThat(judgeProperties.getThreshold()).isEqualTo(0.5);
    assertThat(judgeProperties.getCustomPrompt()).isNull();
    assertThat(judgeProperties.hasProviderConfigured()).isFalse();
  }

  @Test
  void shouldReadThresholdAndCustomPrompt() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.threshold", "0.8");
    properties.setProperty("judge.customPrompt", "Custom prompt text");

    // when
    final JudgeProperties judgeProperties = new JudgeProperties(properties);

    // then
    assertThat(judgeProperties.getThreshold()).isEqualTo(0.8);
    assertThat(judgeProperties.getCustomPrompt()).isEqualTo("Custom prompt text");
  }

  @Test
  void shouldDetectChatModelConfigured() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "openai");

    // when
    final JudgeProperties judgeProperties = new JudgeProperties(properties);

    // then
    assertThat(judgeProperties.hasProviderConfigured()).isTrue();
  }

  @Test
  void shouldRejectThresholdAboveOne() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.threshold", "5.0");

    // when / then
    assertThatThrownBy(() -> new JudgeProperties(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("judge.threshold must be between 0.0 and 1.0");
  }

  @Test
  void shouldRejectNegativeThreshold() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.threshold", "-0.1");

    // when / then
    assertThatThrownBy(() -> new JudgeProperties(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("judge.threshold must be between 0.0 and 1.0");
  }

  @Test
  void shouldCreateTypedProviderConfigForKnownProvider() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "openai");
    properties.setProperty("judge.chatModel.model", "gpt-4o");
    properties.setProperty("judge.chatModel.apiKey", "test-key");
    properties.setProperty("judge.chatModel.timeout", "PT30S");

    // when
    final ProviderConfig config = new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config).isInstanceOf(BaseProviderConfig.OpenAiConfig.class);
    assertThat(config.getProvider()).isEqualTo("openai");
    assertThat(config.getModel()).isEqualTo("gpt-4o");
    assertThat(((BaseProviderConfig.OpenAiConfig) config).getTimeout())
        .isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void shouldCreateBaseProviderConfigForUnknownProvider() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "my-custom-llm");
    properties.setProperty("judge.chatModel.model", "custom-model");

    // when
    final ProviderConfig config = new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config).isExactlyInstanceOf(BaseProviderConfig.GenericConfig.class);
    assertThat(config.getProvider()).isEqualTo("my-custom-llm");
    assertThat(config.getModel()).isEqualTo("custom-model");
    assertThat(config.getCustomProperties()).isEmpty();
  }

  @Test
  void shouldPassCustomPropertiesToGenericConfig() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "my-custom-llm");
    properties.setProperty("judge.chatModel.model", "custom-model");
    properties.setProperty("judge.chatModel.customProperties.endpoint", "http://localhost:8080");
    properties.setProperty("judge.chatModel.customProperties.temperature", "0.7");

    // when
    final ProviderConfig config = new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config).isExactlyInstanceOf(BaseProviderConfig.GenericConfig.class);
    assertThat(config.getCustomProperties())
        .containsEntry("endpoint", "http://localhost:8080")
        .containsEntry("temperature", "0.7")
        .hasSize(2);
  }

  @Test
  void shouldReturnNullProviderConfigWhenNoProviderSet() {
    // given
    final Properties properties = new Properties();

    // when
    final ProviderConfig config = new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config).isNull();
  }

  @Test
  void shouldCreateAzureOpenAiProviderConfig() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "azure-openai");
    properties.setProperty("judge.chatModel.model", "gpt-4o");
    properties.setProperty("judge.chatModel.endpoint", "https://my-resource.openai.azure.com/");
    properties.setProperty("judge.chatModel.apiKey", "test-key");
    properties.setProperty("judge.chatModel.timeout", "PT30S");

    // when
    final ProviderConfig config = new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config).isInstanceOf(AzureOpenAiConfig.class);
    assertThat(config.getProvider()).isEqualTo("azure-openai");
    assertThat(config.getModel()).isEqualTo("gpt-4o");
    final AzureOpenAiConfig azureConfig = (AzureOpenAiConfig) config;
    assertThat(azureConfig.getEndpoint()).isEqualTo("https://my-resource.openai.azure.com/");
    assertThat(azureConfig.getApiKey()).isEqualTo("test-key");
    assertThat(azureConfig.getTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void shouldCreateAzureOpenAiProviderConfigWithoutApiKey() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "azure-openai");
    properties.setProperty("judge.chatModel.model", "gpt-4o");
    properties.setProperty("judge.chatModel.endpoint", "https://my-resource.openai.azure.com/");

    // when
    final ProviderConfig config = new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config).isInstanceOf(AzureOpenAiConfig.class);
    final AzureOpenAiConfig azureConfig = (AzureOpenAiConfig) config;
    assertThat(azureConfig.getApiKey()).isNull();
  }

  @Test
  void shouldParseTemperature() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "openai");
    properties.setProperty("judge.chatModel.model", "gpt-4o");
    properties.setProperty("judge.chatModel.apiKey", "test-key");
    properties.setProperty("judge.chatModel.temperature", "0.7");

    // when
    final BaseProviderConfig config =
        (BaseProviderConfig) new JudgeProperties(properties).toProviderConfig();
    // then
    assertThat(config.getTemperature()).isEqualTo(0.7);
  }

  @Test
  void shouldReturnNullTemperatureWhenNotConfigured() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "openai");
    properties.setProperty("judge.chatModel.model", "gpt-4o");
    properties.setProperty("judge.chatModel.apiKey", "test-key");

    // when
    final BaseProviderConfig config =
        (BaseProviderConfig) new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config.getTemperature()).isNull();
  }

  @Test
  void shouldRejectInvalidTemperature() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.temperature", "not-a-number");

    // when / then
    assertThatThrownBy(() -> new JudgeProperties(properties))
        .isInstanceOf(NumberFormatException.class);
  }

  @Test
  void shouldReturnNullTimeoutWhenNotConfigured() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "openai");
    properties.setProperty("judge.chatModel.model", "gpt-4o");
    properties.setProperty("judge.chatModel.apiKey", "test-key");

    // when
    final BaseProviderConfig config =
        (BaseProviderConfig) new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config.getTimeout()).isNull();
  }

  @Test
  void shouldRejectInvalidTimeout() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.timeout", "1ms");

    // when/then
    assertThatThrownBy(() -> new JudgeProperties(properties))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  void shouldTreatPlaceholderAsAbsent() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.threshold", "${JUDGE_THRESHOLD}");
    properties.setProperty("judge.customPrompt", "${JUDGE_PROMPT}");
    properties.setProperty("judge.chatModel.provider", "${PROVIDER}");

    // when
    final JudgeProperties judgeProperties = new JudgeProperties(properties);

    // then
    assertThat(judgeProperties.getThreshold()).isEqualTo(0.5);
    assertThat(judgeProperties.getCustomPrompt()).isNull();
    assertThat(judgeProperties.hasProviderConfigured()).isFalse();
  }

  @Test
  void shouldParseHeaders() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("judge.chatModel.provider", "openai-compatible");
    properties.setProperty("judge.chatModel.model", "llama3");
    properties.setProperty("judge.chatModel.baseUrl", "http://localhost:11434/v1");
    properties.setProperty("judge.chatModel.headers.X-Test-Header1", "value1");
    properties.setProperty("judge.chatModel.headers.X-Test-Header2", "value2");

    // when
    final ProviderConfig config = new JudgeProperties(properties).toProviderConfig();

    // then
    assertThat(config).isInstanceOf(OpenAiCompatibleConfig.class);
    final OpenAiCompatibleConfig openAiCompatibleConfig = (OpenAiCompatibleConfig) config;

    assertThat(openAiCompatibleConfig.getHeaders()).isNotNull();
    assertThat(openAiCompatibleConfig.getHeaders())
        .containsEntry("X-Test-Header1", "value1")
        .containsEntry("X-Test-Header2", "value2")
        .hasSize(2);
  }
}
