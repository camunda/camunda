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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.ChatModelAdapterProvider;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.ProviderConfig;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.judge.BaseProviderConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.OpenAiCompatibleConfig;
import io.camunda.process.test.impl.judge.OpenAiChatModelAdapterProvider;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;

@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
public class JudgeConfigBootstrapIT {

  static final ChatModelAdapter ADAPTER_A = prompt -> "response from A";
  static final ChatModelAdapter ADAPTER_B = prompt -> "response from B";

  @Configuration
  static class ChatModelAdapterConfig {

    @Bean
    ChatModelAdapter chatModelAdapter() {
      return ADAPTER_A;
    }
  }

  @Configuration
  static class GenericChatModelAdapterConfig {

    @Bean("judge.my-generic")
    ChatModelAdapter genericChatModelAdapter() {
      return ADAPTER_A;
    }
  }

  @Configuration
  static class MultipleChatModelAdapterConfig {

    @Bean("judge.my-custom")
    ChatModelAdapter customChatModelAdapter() {
      return ADAPTER_A;
    }

    @Bean("judge.another-provider")
    ChatModelAdapter anotherChatModelAdapter() {
      return ADAPTER_B;
    }
  }

  @Nested
  @SpringBootTest(classes = JudgeConfigBootstrapIT.class)
  @CamundaSpringProcessTest
  class NotConfigured {

    @Test
    void shouldNotSetJudgeConfigWhenNoPropertiesConfigured() {
      assertThat(CamundaAssert.getJudgeConfig()).isNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key"
      })
  @CamundaSpringProcessTest
  class OpenAiProvider {

    @Test
    void shouldBootstrapOpenAiWithDefaultSettings() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();
      assertThat(config.getThreshold()).isEqualTo(0.5);
      assertThat(config.getCustomPrompt()).isEmpty();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=anthropic",
        "camunda.process-test.judge.chatModel.model=claude-sonnet-4-20250514",
        "camunda.process-test.judge.chatModel.apiKey=test-key"
      })
  @CamundaSpringProcessTest
  class AnthropicProvider {

    @Test
    void shouldBootstrapAnthropicProvider() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=amazon-bedrock",
        "camunda.process-test.judge.chatModel.model=anthropic.claude-v2",
        "camunda.process-test.judge.chatModel.region=us-east-1",
        "camunda.process-test.judge.chatModel.credentials.accessKey=test-access-key",
        "camunda.process-test.judge.chatModel.credentials.secretKey=test-secret-key"
      })
  @CamundaSpringProcessTest
  class BedrockProvider {

    @Test
    void shouldBootstrapBedrockProvider() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai-compatible",
        "camunda.process-test.judge.chatModel.model=llama3",
        "camunda.process-test.judge.chatModel.baseUrl=http://localhost:11434/v1",
        "camunda.process-test.judge.chatModel.apiKey=test-key"
      })
  @CamundaSpringProcessTest
  class OpenAiCompatibleProvider {

    @Test
    void shouldBootstrapOpenAiCompatibleProvider() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai-compatible",
        "camunda.process-test.judge.chatModel.model=llama3",
        "camunda.process-test.judge.chatModel.baseUrl=http://localhost:11434/v1",
        "camunda.process-test.judge.chatModel.headers.X-Test-Header=test-header-value"
      })
  @CamundaSpringProcessTest
  class OpenAiCompatibleProviderWithHeaders {

    @Autowired CamundaProcessTestRuntimeConfiguration runtimeConfig;

    @Test
    void shouldBootstrapOpenAiCompatibleProvider() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();

      final ProviderConfig providerConfig = runtimeConfig.getJudge().toProviderConfig();
      assertThat(providerConfig).isInstanceOf(OpenAiCompatibleConfig.class);
      final Map<String, String> headers = ((OpenAiCompatibleConfig) providerConfig).getHeaders();
      assertThat(headers).isNotNull();
      assertThat(headers).containsEntry("X-Test-Header", "test-header-value");
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key",
        "camunda.process-test.judge.threshold=0.8",
        "camunda.process-test.judge.customPrompt=Custom evaluation criteria"
      })
  @CamundaSpringProcessTest
  class CustomSettings {

    @Test
    void shouldApplyCustomThresholdAndPrompt() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();
      assertThat(config.getThreshold()).isEqualTo(0.8);
      assertThat(config.getCustomPrompt()).hasValue("Custom evaluation criteria");
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=azure-openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.endpoint=https://my-resource.openai.azure.com/",
        "camunda.process-test.judge.chatModel.apiKey=test-key"
      })
  @CamundaSpringProcessTest
  class AzureOpenAiProvider {

    @Test
    void shouldBootstrapAzureOpenAiProvider() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=azure-openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.endpoint=https://my-resource.openai.azure.com/"
      })
  @CamundaSpringProcessTest
  class AzureOpenAiProviderWithoutApiKey {

    @Test
    void shouldBootstrapAzureOpenAiWithDefaultCredentials() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key",
        "camunda.process-test.judge.chatModel.timeout=PT45S"
      })
  @CamundaSpringProcessTest
  class WithTimeout {

    @Autowired CamundaProcessTestRuntimeConfiguration runtimeConfig;

    @Test
    void shouldBindTimeoutProperty() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();

      final BaseProviderConfig providerConfig =
          (BaseProviderConfig) runtimeConfig.getJudge().toProviderConfig();
      assertThat(providerConfig.getTimeout()).isEqualTo(java.time.Duration.ofSeconds(45));
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key",
        "camunda.process-test.judge.chatModel.temperature=0.7"
      })
  @CamundaSpringProcessTest
  class WithTemperature {

    @Autowired CamundaProcessTestRuntimeConfiguration runtimeConfig;

    @Test
    void shouldBindTemperatureProperty() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();

      final BaseProviderConfig providerConfig =
          (BaseProviderConfig) runtimeConfig.getJudge().toProviderConfig();
      assertThat(providerConfig.getTemperature()).isEqualTo(0.7);
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai-compatible",
        "camunda.process-test.judge.chatModel.model=llama3",
        "camunda.process-test.judge.chatModel.baseUrl=http://localhost:11434/v1"
      })
  @CamundaSpringProcessTest
  class OpenAiCompatibleWithoutApiKey {

    @Test
    void shouldBootstrapWithoutApiKey() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.threshold=0.145",
        "camunda.process-test.judge.customPrompt=Custom prompt",
      })
  @CamundaSpringProcessTest
  @Import(JudgeConfigBootstrapIT.ChatModelAdapterConfig.class)
  class WithSingleChatModelAdapterBean {

    @Test
    void shouldUseSingleBeanWhenNoProviderConfigured() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isSameAs(ADAPTER_A);
      assertThat(config.getThreshold()).isEqualTo(0.145);
      assertThat(config.getCustomPrompt()).hasValue("Custom prompt");
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=my-custom",
        "camunda.process-test.judge.threshold=0.7",
      })
  @CamundaSpringProcessTest
  @Import(JudgeConfigBootstrapIT.MultipleChatModelAdapterConfig.class)
  class WithMultipleBeansAndMatchingProvider {

    @Test
    void shouldSelectBeanByProviderName() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isSameAs(ADAPTER_A);
      assertThat(config.getThreshold()).isEqualTo(0.7);
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key",
      })
  @CamundaSpringProcessTest
  @Import(JudgeConfigBootstrapIT.MultipleChatModelAdapterConfig.class)
  class WithMultipleBeansAndNoMatch {

    @Test
    void shouldFallBackToSpiWhenNoBeanMatchesProvider() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      // SPI-bootstrapped adapter (from Langchain4j), not one of the beans
      assertThat(config.getChatModel()).isNotSameAs(ADAPTER_A).isNotSameAs(ADAPTER_B);
    }
  }

  @Nested
  @SpringBootTest(classes = JudgeConfigBootstrapIT.class)
  @CamundaSpringProcessTest
  @Import(JudgeConfigBootstrapIT.MultipleChatModelAdapterConfig.class)
  class WithMultipleBeansAndNoProviderProperty {

    @Test
    void shouldNotBootstrapWhenMultipleBeansAndNoProvider() {
      assertThat(CamundaAssert.getJudgeConfig()).isNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = JudgeConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.judge.chatModel.provider=my-generic",
        "camunda.process-test.judge.chatModel.model=custom-model",
        "camunda.process-test.judge.chatModel.customProperties.endpoint=http://localhost:8080",
        "camunda.process-test.judge.chatModel.customProperties.temperature=0.7",
        "camunda.process-test.judge.threshold=0.6",
      })
  @CamundaSpringProcessTest
  @Import(JudgeConfigBootstrapIT.GenericChatModelAdapterConfig.class)
  class GenericProviderWithCustomProperties {

    @Autowired CamundaProcessTestRuntimeConfiguration runtimeConfig;

    @Test
    void shouldBootstrapAndBindCustomProperties() {
      // judge config bootstrapped via the single bean
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isSameAs(ADAPTER_A);
      assertThat(config.getThreshold()).isEqualTo(0.6);

      // custom properties bound to GenericConfig
      final ProviderConfig providerConfig = runtimeConfig.getJudge().toProviderConfig();
      assertThat(providerConfig).isInstanceOf(BaseProviderConfig.GenericConfig.class);
      assertThat(providerConfig.getProvider()).isEqualTo("my-generic");
      assertThat(providerConfig.getModel()).isEqualTo("custom-model");
      assertThat(providerConfig.getCustomProperties())
          .containsEntry("endpoint", "http://localhost:8080")
          .containsEntry("temperature", "0.7")
          .hasSize(2);
    }
  }

  @Nested
  class InvalidConfiguration {

    private final ChatModelAdapterProvider provider = new OpenAiChatModelAdapterProvider();

    @Test
    void shouldThrowWhenRequiredFieldMissing() {
      final ProviderConfig config = new BaseProviderConfig.OpenAiConfig(null, "api-key");
      assertThatThrownBy(() -> provider.create(config)).isInstanceOf(IllegalStateException.class);
    }
  }
}
