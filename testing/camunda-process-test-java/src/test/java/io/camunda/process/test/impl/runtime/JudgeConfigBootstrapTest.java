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
package io.camunda.process.test.impl.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.impl.runtime.properties.JudgeProperties;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class JudgeConfigBootstrapTest {

  @Test
  void shouldReturnNullWhenNotConfigured() {
    // given
    final Properties raw = new Properties();
    final JudgeProperties judgeProperties = new JudgeProperties(raw);

    // when
    final JudgeConfig config = JudgeConfigBootstrap.bootstrap(judgeProperties, raw);

    // then
    assertThat(config).isNull();
  }

  @Test
  void shouldThrowWhenNoFactoryHandlesProperties() {
    // given - provider "openai" is not handled by TestChatModelAdapterFactory
    final Properties raw = new Properties();
    raw.setProperty("judge.chatModel.provider", "openai");
    final JudgeProperties judgeProperties = new JudgeProperties(raw);

    // when / then
    assertThatThrownBy(() -> JudgeConfigBootstrap.bootstrap(judgeProperties, raw))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "No ChatModelAdapterFactory could create an adapter for provider 'openai'.");
  }

  @Test
  void shouldBootstrapWhenFactoryDiscoveredViaSpi() {
    // given - provider "test" is handled by TestChatModelAdapterFactory (registered via SPI)
    final Properties raw = new Properties();
    raw.setProperty("judge.chatModel.provider", "test");
    final JudgeProperties judgeProperties = new JudgeProperties(raw);

    // when
    final JudgeConfig config = JudgeConfigBootstrap.bootstrap(judgeProperties, raw);

    // then
    assertThat(config).isNotNull();
    assertThat(config.getChatModel()).isNotNull();
  }
}
