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

import io.camunda.client.api.search.enums.ProtectionMode;
import org.junit.jupiter.api.Test;

class VariableMetadataInputTest {

  @Test
  void shouldKeepProtectionsOfBothWhenCombined() {
    // given
    final VariableMetadataInput redacted = VariableMetadataInput.redacted();
    final VariableMetadataInput other = VariableMetadataInput.of(ProtectionMode.UNKNOWN_ENUM_VALUE);

    // when
    final VariableMetadataInput combined = redacted.and(other);

    // then
    assertThat(combined.getProtectionModes())
        .containsExactlyInAnyOrder(ProtectionMode.REDACT, ProtectionMode.UNKNOWN_ENUM_VALUE);
    assertThat(redacted.getProtectionModes()).containsExactly(ProtectionMode.REDACT);
    assertThat(other.getProtectionModes()).containsExactly(ProtectionMode.UNKNOWN_ENUM_VALUE);
  }

  @Test
  void shouldTreatDeclaringNothingAsNeutralWhenCombined() {
    // given
    final VariableMetadataInput nothing = VariableMetadataInput.builder().build();

    // when / then
    assertThat(nothing.and(VariableMetadataInput.redacted()))
        .isEqualTo(VariableMetadataInput.redacted());
    assertThat(VariableMetadataInput.redacted().and(nothing))
        .isEqualTo(VariableMetadataInput.redacted());
    assertThat(nothing.and(nothing).getProtectionModes()).isNull();
  }

  @Test
  void shouldStayExplicitlyEmptyWhenCombinedWithEmpty() {
    // given
    final VariableMetadataInput empty = VariableMetadataInput.of();

    // when / then
    assertThat(empty.and(empty).getProtectionModes()).isEmpty();
    assertThat(empty.and(VariableMetadataInput.redacted()).getProtectionModes())
        .containsExactly(ProtectionMode.REDACT);
  }

  @Test
  void shouldAccumulateProtectionsDeclaredOnTheBuilder() {
    // when
    final VariableMetadataInput metadata =
        VariableMetadataInput.builder()
            .protectionModes(ProtectionMode.REDACT)
            .protectionModes(ProtectionMode.UNKNOWN_ENUM_VALUE)
            .protectionModes()
            .build();

    // then
    assertThat(metadata.getProtectionModes())
        .containsExactlyInAnyOrder(ProtectionMode.REDACT, ProtectionMode.UNKNOWN_ENUM_VALUE);
  }
}
