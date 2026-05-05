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
import io.camunda.process.test.api.judge.JudgeConfig;
import org.junit.jupiter.api.Test;

class JudgeConfigImplTest {

  private static final ChatModelAdapter ADAPTER = prompt -> "response";

  @Test
  void shouldDefaultResolveDocumentsToFalse() {
    assertThat(JudgeConfig.defaults().isResolveDocuments()).isFalse();
    assertThat(JudgeConfig.of(ADAPTER).isResolveDocuments()).isFalse();
  }

  @Test
  void shouldEnableResolveDocuments() {
    final JudgeConfig config = JudgeConfig.of(ADAPTER).withResolveDocuments(true);

    assertThat(config.isResolveDocuments()).isTrue();
  }

  @Test
  void shouldPreserveResolveDocumentsAcrossOtherWithCalls() {
    final JudgeConfig config =
        JudgeConfig.of(ADAPTER)
            .withResolveDocuments(true)
            .withThreshold(0.7)
            .withCustomPrompt("custom");

    assertThat(config.isResolveDocuments()).isTrue();
    assertThat(config.getThreshold()).isEqualTo(0.7);
    assertThat(config.getCustomPrompt()).hasValue("custom");
  }

  @Test
  void shouldPreserveOtherSettingsAcrossWithResolveDocuments() {
    final JudgeConfig config = JudgeConfig.of(ADAPTER, 0.9, "custom").withResolveDocuments(true);

    assertThat(config.isResolveDocuments()).isTrue();
    assertThat(config.getThreshold()).isEqualTo(0.9);
    assertThat(config.getCustomPrompt()).hasValue("custom");
    assertThat(config.getChatModel()).isSameAs(ADAPTER);
  }
}
