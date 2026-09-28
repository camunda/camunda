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

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.JudgeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JudgeConfigImplTest {

  private static final ChatModelAdapter ADAPTER = prompt -> "response";

  @Test
  void shouldDefaultAttachDocumentsToFalse() {
    assertThat(JudgeConfig.defaults().isAttachDocuments()).isFalse();
    assertThat(JudgeConfig.of(ADAPTER).isAttachDocuments()).isFalse();
  }

  @Test
  void shouldEnableAttachDocuments() {
    final JudgeConfig config = JudgeConfig.of(ADAPTER).withAttachDocuments(true);

    assertThat(config.isAttachDocuments()).isTrue();
  }

  @Test
  void shouldPreserveAttachDocumentsAcrossOtherWithCalls() {
    final JudgeConfig config =
        JudgeConfig.of(ADAPTER)
            .withAttachDocuments(true)
            .withThreshold(0.7)
            .withCustomPrompt("custom");

    assertThat(config.isAttachDocuments()).isTrue();
    assertThat(config.getThreshold()).isEqualTo(0.7);
    assertThat(config.getCustomPrompt()).hasValue("custom");
  }

  @Test
  void shouldPreserveOtherSettingsAcrossWithAttachDocuments() {
    final JudgeConfig config = JudgeConfig.of(ADAPTER, 0.9, "custom").withAttachDocuments(true);

    assertThat(config.isAttachDocuments()).isTrue();
    assertThat(config.getThreshold()).isEqualTo(0.9);
    assertThat(config.getCustomPrompt()).hasValue("custom");
    assertThat(config.getChatModel()).isSameAs(ADAPTER);
  }

  @Test
  void shouldDefaultPenaltyExponentToOne() {
    assertThat(JudgeConfig.defaults().getPenaltyExponent()).isEqualTo(1.0);
    assertThat(JudgeConfig.of(ADAPTER).getPenaltyExponent()).isEqualTo(1.0);
  }

  @Test
  void shouldSetPenaltyExponent() {
    final JudgeConfig config = JudgeConfig.of(ADAPTER).withPenaltyExponent(2.0);

    assertThat(config.getPenaltyExponent()).isEqualTo(2.0);
  }

  @Test
  void shouldPreservePenaltyExponentAcrossOtherWithCalls() {
    final JudgeConfig config =
        JudgeConfig.of(ADAPTER).withPenaltyExponent(3.0).withThreshold(0.7).withCustomPrompt("c");

    assertThat(config.getPenaltyExponent()).isEqualTo(3.0);
    assertThat(config.getThreshold()).isEqualTo(0.7);
  }

  @ParameterizedTest
  @ValueSource(
      doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
  void shouldRejectInvalidPenaltyExponent(final double invalidExponent) {
    assertThatThrownBy(() -> JudgeConfig.of(ADAPTER).withPenaltyExponent(invalidExponent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("penaltyExponent must be a positive, finite number");
  }
}
