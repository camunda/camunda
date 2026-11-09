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
package io.camunda.client.api.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.search.enums.ProtectionMode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class VariableMetadataTest {

  @Test
  void shouldReplaceProtectionsDeclaredOnTheBuilderBefore() {
    // given
    final VariableMetadata.Builder builder =
        VariableMetadata.builder().protectionModes(ProtectionMode.REDACT);

    // when
    final VariableMetadata metadata = builder.protectionModes().build();

    // then
    // the last declaration wins, declaring no modes replaces the earlier ones with none
    assertThat(metadata.getProtectionModes()).isEmpty();
  }

  @Test
  void shouldHaveNoProtectionsByDefault() {
    // when / then
    assertThat(VariableMetadata.builder().build().getProtectionModes()).isEmpty();
    assertThat(VariableMetadata.of()).isEqualTo(VariableMetadata.builder().build());
  }

  @Test
  void shouldRejectUnknownProtectionMode() {
    // when / then
    assertThatThrownBy(() -> VariableMetadata.of(ProtectionMode.UNKNOWN_ENUM_VALUE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN_ENUM_VALUE");
    assertThatThrownBy(
            () ->
                VariableMetadata.of(
                    Arrays.asList(ProtectionMode.REDACT, ProtectionMode.UNKNOWN_ENUM_VALUE)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> VariableMetadata.of((ProtectionMode) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldNotChangeTheBuilderWhenAModeIsRejected() {
    // given
    final VariableMetadata.Builder builder =
        VariableMetadata.builder().protectionModes(ProtectionMode.REDACT);

    // when
    assertThatThrownBy(() -> builder.protectionModes(ProtectionMode.UNKNOWN_ENUM_VALUE))
        .isInstanceOf(IllegalArgumentException.class);

    // then
    assertThat(builder.build().getProtectionModes()).containsExactly(ProtectionMode.REDACT);
  }
}
