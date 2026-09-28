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
package io.camunda.process.test.impl.judge.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.impl.judge.BaseProviderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class JevChatModelAdapterProviderTest {

  private final JevChatModelAdapterProvider provider = new JevChatModelAdapterProvider();

  @Test
  void shouldExposeProviderNameJev() {
    assertThat(provider.getProviderName()).isEqualTo(BaseProviderConfig.PROVIDER_JEV);
  }

  @Test
  void shouldCreateAdapterWhenApiKeyIsSet() {
    // given
    final BaseProviderConfig.JevConfig config =
        new BaseProviderConfig.JevConfig("jev-latest", "test-key", null);

    // when
    final ChatModelAdapter adapter = provider.create(config);

    // then
    assertThat(adapter).isInstanceOf(JevChatModelAdapter.class);
  }

  @Test
  void shouldCreateAdapterWithCustomBaseUrl() {
    // given
    final BaseProviderConfig.JevConfig config =
        new BaseProviderConfig.JevConfig(
            "jev-latest", "test-key", "http://localhost:8080/systemone");

    // when
    final ChatModelAdapter adapter = provider.create(config);

    // then
    assertThat(adapter).isInstanceOf(JevChatModelAdapter.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldThrowWhenApiKeyIsMissing(final String apiKey) {
    // given
    final BaseProviderConfig.JevConfig config =
        new BaseProviderConfig.JevConfig("jev-latest", apiKey, null);

    // when / then
    assertThatThrownBy(() -> provider.create(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("judge.chatModel.apiKey");
  }
}
