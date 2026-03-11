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
import io.camunda.process.test.api.judge.JudgeConfigBootstrapData;
import io.camunda.process.test.api.judge.JudgeConfigBootstrapData.OpenAiConfig;
import io.camunda.process.test.api.judge.JudgeConfigBootstrapData.ProviderConfig;
import io.camunda.process.test.impl.judge.JudgeConfigBootstrap;
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

  @Configuration
  static class ChatModelAdapterConfig {

    @Bean
    ChatModelAdapter chatModelAdapter() {
      return prompt -> "mocked response";
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
      assertThat(config.getCustomPrompt()).isNull();
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
      assertThat(config.getCustomPrompt()).isEqualTo("Custom evaluation criteria");
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
  class WithChatModelAdapterBean {

    @Autowired private ChatModelAdapter chatModelAdapter;

    @Test
    void shouldUseSpringBeanWhenChatModelAdapterBeanIsPresent() {
      final JudgeConfig config = CamundaAssert.getJudgeConfig();
      assertThat(config).isNotNull();
      assertThat(config.getChatModel()).isSameAs(chatModelAdapter);
      assertThat(config.getThreshold()).isEqualTo(0.145);
      assertThat(config.getCustomPrompt()).isEqualTo("Custom prompt");
    }
  }

  @Nested
  class InvalidConfiguration {

    private final JudgeConfigBootstrap bootstrap = new JudgeConfigBootstrap();

    @Test
    void shouldReturnNullWhenProviderNotConfigured() {
      final JudgeConfigBootstrapData data = JudgeConfigBootstrapData.builder().build();
      final JudgeConfig config = bootstrap.bootstrap(data);
      assertThat(config).isNull();
    }

    @Test
    void shouldReturnNullWhenProviderIsUnknown() {
      final JudgeConfigBootstrapData data =
          JudgeConfigBootstrapData.builder()
              .providerConfig(new ProviderConfig("unknown-provider", "test-model") {})
              .build();
      final JudgeConfig config = bootstrap.bootstrap(data);
      assertThat(config).isNull();
    }

    @Test
    void shouldThrowWhenRequiredFieldMissing() {
      final JudgeConfigBootstrapData data =
          JudgeConfigBootstrapData.builder()
              .providerConfig(new OpenAiConfig(null, "api-key"))
              .build();
      assertThatThrownBy(() -> bootstrap.bootstrap(data)).isInstanceOf(IllegalStateException.class);
    }
  }
}
