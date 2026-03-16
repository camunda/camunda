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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.impl.configuration.JudgeConfiguration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class JudgeConfigResolverTest {

  private static final ChatModelAdapter ADAPTER_A = prompt -> "response A";
  private static final ChatModelAdapter ADAPTER_B = prompt -> "response B";

  @Mock private ApplicationContext applicationContext;

  @Test
  void shouldReturnEmptyWhenNothingConfigured() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class)).thenReturn(Map.of());

    final Optional<JudgeConfig> result =
        JudgeConfigResolver.resolve(applicationContext, new JudgeConfiguration());

    assertThat(result).isEmpty();
  }

  @Test
  void shouldUseSingleBean() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class))
        .thenReturn(Map.of("myAdapter", ADAPTER_A));

    final JudgeConfiguration config = new JudgeConfiguration();
    config.setThreshold(0.8);
    config.setCustomPrompt("Custom criteria");

    final Optional<JudgeConfig> result = JudgeConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getChatModel()).isSameAs(ADAPTER_A);
    assertThat(result.get().getThreshold()).isEqualTo(0.8);
    assertThat(result.get().getCustomPrompt()).hasValue("Custom criteria");
  }

  @Test
  void shouldUseSingleBeanWithDefaults() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class))
        .thenReturn(Map.of("myAdapter", ADAPTER_A));

    final Optional<JudgeConfig> result =
        JudgeConfigResolver.resolve(applicationContext, new JudgeConfiguration());

    assertThat(result).isPresent();
    assertThat(result.get().getChatModel()).isSameAs(ADAPTER_A);
    assertThat(result.get().getThreshold()).isEqualTo(0.5);
    assertThat(result.get().getCustomPrompt()).isEmpty();
  }

  @Test
  void shouldMatchSingleBeanByNameWhenProviderConfigured() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class))
        .thenReturn(Map.of("my-adapter", ADAPTER_A));

    final JudgeConfiguration config = new JudgeConfiguration();
    config.getChatModel().setProvider("my-adapter");

    final Optional<JudgeConfig> result = JudgeConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getChatModel()).isSameAs(ADAPTER_A);
  }

  @Test
  void shouldFallBackToSpiWhenSingleBeanAndProviderDoesNotMatch() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class))
        .thenReturn(Map.of("my-adapter", ADAPTER_A));

    final JudgeConfiguration config = new JudgeConfiguration();
    config.getChatModel().setProvider("openai");
    config.getChatModel().setModel("gpt-4o");
    config.getChatModel().setApiKey("sk-test");

    final Optional<JudgeConfig> result = JudgeConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getChatModel()).isNotSameAs(ADAPTER_A);
  }

  @Test
  void shouldSelectBeanByProviderName() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class))
        .thenReturn(Map.of("my-custom", ADAPTER_A, "another", ADAPTER_B));

    final JudgeConfiguration config = new JudgeConfiguration();
    config.getChatModel().setProvider("my-custom");

    final Optional<JudgeConfig> result = JudgeConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getChatModel()).isSameAs(ADAPTER_A);
  }

  @Test
  void shouldFallBackToSpiWhenMultipleBeansAndNoMatch() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class))
        .thenReturn(Map.of("custom-a", ADAPTER_A, "custom-b", ADAPTER_B));

    final JudgeConfiguration config = new JudgeConfiguration();
    config.getChatModel().setProvider("openai");
    config.getChatModel().setModel("gpt-4o");
    config.getChatModel().setApiKey("sk-test");

    final Optional<JudgeConfig> result = JudgeConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getChatModel()).isNotSameAs(ADAPTER_A).isNotSameAs(ADAPTER_B);
  }

  @Test
  void shouldReturnEmptyWhenMultipleBeansAndNoProvider() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class))
        .thenReturn(Map.of("custom-a", ADAPTER_A, "custom-b", ADAPTER_B));

    final Optional<JudgeConfig> result =
        JudgeConfigResolver.resolve(applicationContext, new JudgeConfiguration());

    assertThat(result).isEmpty();
  }

  @Test
  void shouldFallBackToSpiWhenNoBeansPresent() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class)).thenReturn(Map.of());

    final JudgeConfiguration config = new JudgeConfiguration();
    config.getChatModel().setProvider("openai");
    config.getChatModel().setModel("gpt-4o");
    config.getChatModel().setApiKey("sk-test");

    final Optional<JudgeConfig> result = JudgeConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getChatModel()).isNotNull();
  }

  @Test
  void shouldThrowWhenProviderConfiguredButNotResolvable() {
    when(applicationContext.getBeansOfType(ChatModelAdapter.class)).thenReturn(Map.of());

    final JudgeConfiguration config = new JudgeConfiguration();
    config.getChatModel().setProvider("unknown-provider");
    config.getChatModel().setModel("some-model");

    assertThatThrownBy(() -> JudgeConfigResolver.resolve(applicationContext, config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no ChatModelAdapterProvider could be resolved");
  }
}
