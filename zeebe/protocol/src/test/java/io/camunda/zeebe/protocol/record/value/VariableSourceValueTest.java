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
package io.camunda.zeebe.protocol.record.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class VariableSourceValueTest {

  @Test
  void shouldHaveNoCausingElementWhenNotSet() {
    // given a source that only has a type
    final ImmutableVariableSourceValue.Builder builder =
        ImmutableVariableSourceValue.builder().withType(VariableOperationType.API);

    // when
    final VariableSourceValue source = builder.build();

    // then
    assertThat(source.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(source.getElementId()).isEmpty();
  }

  @Test
  void shouldKeepCausingElementWhenSet() {
    // given
    final ImmutableVariableSourceValue.Builder builder =
        ImmutableVariableSourceValue.builder()
            .withType(VariableOperationType.JOB_COMPLETION)
            .withElementInstanceKey(42L)
            .withElementId("ChargeCard");

    // when
    final VariableSourceValue source = builder.build();

    // then
    assertThat(source.getType()).isEqualTo(VariableOperationType.JOB_COMPLETION);
    assertThat(source.getElementInstanceKey()).isEqualTo(42L);
    assertThat(source.getElementId()).isEqualTo("ChargeCard");
  }
}
