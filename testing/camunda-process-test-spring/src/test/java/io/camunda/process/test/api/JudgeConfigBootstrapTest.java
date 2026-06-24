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
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.JudgeConfiguration;
import io.camunda.process.test.impl.configuration.LegacyCamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.judge.BaseProviderConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.AmazonBedrockConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.AnthropicConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.AzureOpenAiConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.GenericConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.OpenAiCompatibleConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig.OpenAiConfig;
import io.camunda.process.test.impl.judge.JudgeConfigResolver;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties({
  CamundaProcessTestRuntimeConfiguration.class,
  LegacyCamundaProcessTestRuntimeConfiguration.class
})
public class JudgeConfigBootstrapTest {

  static final ChatModelAdapter ADAPTER_A = prompt -> "response from A";
  static final ChatModelAdapter ADAPTER_B = prompt -> "response from B";

  @Nested
  class NotConfigured {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldNotResolveJudgeConfigWhenNoPropertiesConfigured() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.hasProviderConfigured()).isFalse();

      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration)).isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key"
      })
  class OpenAiProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapOpenAiWithDefaultSettings() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getProvider()).isEqualTo("openai");
                assertThat(chatModel.getModel()).isEqualTo("gpt-4o");
                assertThat(chatModel.getApiKey()).isEqualTo("test-key");
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              OpenAiConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("gpt-4o");
                assertThat(providerConfig.getApiKey()).isEqualTo("test-key");
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getChatModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=anthropic",
        "camunda.process-test.judge.chatModel.model=claude-sonnet-4-20250514",
        "camunda.process-test.judge.chatModel.apiKey=test-key"
      })
  class AnthropicProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapAnthropicWithDefaultSettings() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getProvider()).isEqualTo("anthropic");
                assertThat(chatModel.getModel()).isEqualTo("claude-sonnet-4-20250514");
                assertThat(chatModel.getApiKey()).isEqualTo("test-key");
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              AnthropicConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("claude-sonnet-4-20250514");
                assertThat(providerConfig.getApiKey()).isEqualTo("test-key");
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getChatModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=amazon-bedrock",
        "camunda.process-test.judge.chatModel.model=anthropic.claude-v2",
        "camunda.process-test.judge.chatModel.region=us-east-1",
        "camunda.process-test.judge.chatModel.credentials.accessKey=test-access-key",
        "camunda.process-test.judge.chatModel.credentials.secretKey=test-secret-key"
      })
  class BedrockProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapBedrockProvider() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getProvider()).isEqualTo("amazon-bedrock");
                assertThat(chatModel.getModel()).isEqualTo("anthropic.claude-v2");
                assertThat(chatModel.getRegion()).isEqualTo("us-east-1");
                assertThat(chatModel.getCredentials().getAccessKey()).isEqualTo("test-access-key");
                assertThat(chatModel.getCredentials().getSecretKey()).isEqualTo("test-secret-key");
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              AmazonBedrockConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("anthropic.claude-v2");
                assertThat(providerConfig.getRegion()).isEqualTo("us-east-1");
                assertThat(providerConfig.getCredentialsAccessKey()).isEqualTo("test-access-key");
                assertThat(providerConfig.getCredentialsSecretKey()).isEqualTo("test-secret-key");
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getChatModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai-compatible",
        "camunda.process-test.judge.chatModel.model=llama3",
        "camunda.process-test.judge.chatModel.baseUrl=http://localhost:11434/v1",
        "camunda.process-test.judge.chatModel.apiKey=test-key"
      })
  class OpenAiCompatibleProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapOpenAiCompatibleProvider() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getProvider()).isEqualTo("openai-compatible");
                assertThat(chatModel.getModel()).isEqualTo("llama3");
                assertThat(chatModel.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(chatModel.getApiKey()).isEqualTo("test-key");
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              OpenAiCompatibleConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("llama3");
                assertThat(providerConfig.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(providerConfig.getApiKey()).isEqualTo("test-key");
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getChatModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai-compatible",
        "camunda.process-test.judge.chatModel.model=llama3",
        "camunda.process-test.judge.chatModel.baseUrl=http://localhost:11434/v1",
        "camunda.process-test.judge.chatModel.headers.X-Test-Header=test-header-value"
      })
  class OpenAiCompatibleProviderWithHeaders {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapOpenAiCompatibleProvider() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getProvider()).isEqualTo("openai-compatible");
                assertThat(chatModel.getModel()).isEqualTo("llama3");
                assertThat(chatModel.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(chatModel.getHeaders())
                    .containsEntry("X-Test-Header", "test-header-value");
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              OpenAiCompatibleConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("llama3");
                assertThat(providerConfig.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(providerConfig.getHeaders())
                    .isNotNull()
                    .containsEntry("X-Test-Header", "test-header-value");
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getChatModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key",
        "camunda.process-test.judge.threshold=0.8",
        "camunda.process-test.judge.customPrompt=Custom evaluation criteria"
      })
  class CustomSettings {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldApplyCustomThresholdAndPrompt() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(0.8);
      assertThat(judgeConfiguration.getCustomPrompt()).isEqualTo("Custom evaluation criteria");

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getChatModel()).isNotNull();
                assertThat(config.getThreshold()).isEqualTo(0.8);
                assertThat(config.getCustomPrompt()).hasValue("Custom evaluation criteria");
              });
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=azure-openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.endpoint=https://my-resource.openai.azure.com/",
        "camunda.process-test.judge.chatModel.apiKey=test-key"
      })
  class AzureOpenAiProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapAzureOpenAiProvider() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getModel()).isEqualTo("gpt-4o");
                assertThat(chatModel.getEndpoint())
                    .isEqualTo("https://my-resource.openai.azure.com/");
                assertThat(chatModel.getApiKey()).isEqualTo("test-key");
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              AzureOpenAiConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("gpt-4o");
                assertThat(providerConfig.getEndpoint())
                    .isEqualTo("https://my-resource.openai.azure.com/");
                assertThat(providerConfig.getApiKey()).isEqualTo("test-key");
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getChatModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=azure-openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.endpoint=https://my-resource.openai.azure.com/"
      })
  class AzureOpenAiProviderWithoutApiKey {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapAzureOpenAiWithDefaultCredentials() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getModel()).isEqualTo("gpt-4o");
                assertThat(chatModel.getEndpoint())
                    .isEqualTo("https://my-resource.openai.azure.com/");
                assertThat(chatModel.getApiKey()).isNull();
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              AzureOpenAiConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("gpt-4o");
                assertThat(providerConfig.getEndpoint())
                    .isEqualTo("https://my-resource.openai.azure.com/");
                assertThat(providerConfig.getApiKey()).isNull();
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getChatModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key",
        "camunda.process-test.judge.chatModel.timeout=PT45S"
      })
  class WithTimeout {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;

    @Test
    void shouldBindTimeoutProperty() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> assertThat(chatModel.getTimeout()).isEqualTo(Duration.ofSeconds(45)));

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              BaseProviderConfig.class,
              providerConfig ->
                  assertThat(providerConfig.getTimeout()).isEqualTo(Duration.ofSeconds(45)));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key",
        "camunda.process-test.judge.chatModel.temperature=0.7"
      })
  class WithTemperature {

    @Autowired CamundaProcessTestRuntimeConfiguration configuration;

    @Test
    void shouldBindTemperatureProperty() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(chatModel -> assertThat(chatModel.getTemperature()).isEqualTo(0.7));

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              BaseProviderConfig.class,
              providerConfig -> assertThat(providerConfig.getTemperature()).isEqualTo(0.7));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai-compatible",
        "camunda.process-test.judge.chatModel.model=llama3",
        "camunda.process-test.judge.chatModel.baseUrl=http://localhost:11434/v1"
      })
  class OpenAiCompatibleWithoutApiKey {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapWithoutApiKey() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(JudgeConfig.DEFAULT_THRESHOLD);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getModel()).isEqualTo("llama3");
                assertThat(chatModel.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(chatModel.getApiKey()).isNull();
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              OpenAiCompatibleConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("llama3");
                assertThat(providerConfig.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(providerConfig.getApiKey()).isNull();
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getChatModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.threshold=0.145",
        "camunda.process-test.judge.customPrompt=Custom prompt",
      })
  class WithSingleChatModelAdapterBean {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldUseSingleBeanWhenNoProviderConfigured() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(0.145);
      assertThat(judgeConfiguration.getCustomPrompt()).isEqualTo("Custom prompt");
      assertThat(judgeConfiguration.hasProviderConfigured()).isFalse();

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getChatModel()).isEqualTo(ADAPTER_A);
                assertThat(config.getThreshold()).isEqualTo(0.145);
                assertThat(config.getCustomPrompt()).hasValue("Custom prompt");
              });
    }

    @Configuration
    static class ChatModelAdapterConfig {

      @Bean
      ChatModelAdapter chatModelAdapter() {
        return ADAPTER_A;
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=my-custom",
        "camunda.process-test.judge.threshold=0.7",
      })
  class WithMultipleBeansAndMatchingProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldSelectBeanByProviderName() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(0.7);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(chatModel -> assertThat(chatModel.getProvider()).isEqualTo("my-custom"));

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getChatModel()).isEqualTo(ADAPTER_A);
                assertThat(config.getThreshold()).isEqualTo(0.7);
                assertThat(config.getCustomPrompt()).isEmpty();
              });
    }

    @Configuration
    static class MultipleChatModelAdapterConfig {

      @Bean("my-custom")
      ChatModelAdapter customChatModelAdapter() {
        return ADAPTER_A;
      }

      @Bean("another-provider")
      ChatModelAdapter anotherChatModelAdapter() {
        return ADAPTER_B;
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        "camunda.process-test.judge.chatModel.model=gpt-4o",
        "camunda.process-test.judge.chatModel.apiKey=test-key",
      })
  class WithMultipleBeansAndNoMatch {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldFallBackToSpiWhenNoBeanMatchesProvider() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(chatModel -> assertThat(chatModel.getProvider()).isEqualTo("openai"));

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                // SPI-bootstrapped adapter (from Langchain4j), not one of the beans
                assertThat(config.getChatModel())
                    .isNotNull()
                    .isNotSameAs(ADAPTER_A)
                    .isNotSameAs(ADAPTER_B);
              });
    }

    @Configuration
    static class MultipleChatModelAdapterConfig {

      @Bean("my-custom")
      ChatModelAdapter customChatModelAdapter() {
        return ADAPTER_A;
      }

      @Bean("another-provider")
      ChatModelAdapter anotherChatModelAdapter() {
        return ADAPTER_B;
      }
    }
  }

  @Nested
  class WithMultipleBeansAndNoProviderProperty {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldNotBootstrapWhenMultipleBeansAndNoProvider() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.hasProviderConfigured()).isFalse();

      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration)).isEmpty();
    }

    @Configuration
    static class MultipleChatModelAdapterConfig {

      @Bean("my-custom")
      ChatModelAdapter customChatModelAdapter() {
        return ADAPTER_A;
      }

      @Bean("another-provider")
      ChatModelAdapter anotherChatModelAdapter() {
        return ADAPTER_B;
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=my-generic",
        "camunda.process-test.judge.chatModel.model=custom-model",
        "camunda.process-test.judge.chatModel.customProperties.endpoint=http://localhost:8080",
        "camunda.process-test.judge.chatModel.customProperties.temperature=0.7",
        "camunda.process-test.judge.threshold=0.6",
      })
  class GenericProviderWithCustomProperties {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapAndBindCustomProperties() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getThreshold()).isEqualTo(0.6);
      assertThat(judgeConfiguration.getCustomPrompt()).isNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getProvider()).isEqualTo("my-generic");
                assertThat(chatModel.getModel()).isEqualTo("custom-model");
                assertThat(chatModel.getCustomProperties())
                    .containsEntry("endpoint", "http://localhost:8080")
                    .containsEntry("temperature", "0.7")
                    .hasSize(2);
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              GenericConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getProvider()).isEqualTo("my-generic");
                assertThat(providerConfig.getModel()).isEqualTo("custom-model");
                assertThat(providerConfig.getCustomProperties())
                    .containsEntry("endpoint", "http://localhost:8080")
                    .containsEntry("temperature", "0.7")
                    .hasSize(2);
              });

      // verify judge config creation
      assertThat(JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getChatModel())
                    .isNotNull()
                    .isSameAs(ADAPTER_A); // single bean selected as provider
                assertThat(config.getThreshold()).isEqualTo(0.6);
              });
    }

    @Configuration
    static class GenericChatModelAdapterConfig {

      @Bean("my-generic")
      ChatModelAdapter genericChatModelAdapter() {
        return ADAPTER_A;
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.judge.chatModel.provider=openai",
        // no model configured, which is required for openai provider
        "camunda.process-test.judge.chat-model.api-key=test-key",
      })
  class InvalidConfiguration {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldThrowWhenRequiredFieldMissing() {
      final JudgeConfiguration judgeConfiguration = configuration.getJudge();

      // verify properties binding
      assertThat(judgeConfiguration).isNotNull();
      assertThat(judgeConfiguration.getChatModel())
          .satisfies(
              chatModel -> {
                assertThat(chatModel.getProvider()).isEqualTo("openai");
                assertThat(chatModel.getModel()).isNull();
                assertThat(chatModel.getApiKey()).isEqualTo("test-key");
              });

      // verify provider config creation
      assertThat(judgeConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              OpenAiConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isNull();
                assertThat(providerConfig.getApiKey()).isEqualTo("test-key");
              });

      // verify judge config creation fails
      assertThatThrownBy(() -> JudgeConfigResolver.resolve(applicationContext, judgeConfiguration))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Field 'model' is required for the 'openai' provider but was not set.");
    }
  }
}
