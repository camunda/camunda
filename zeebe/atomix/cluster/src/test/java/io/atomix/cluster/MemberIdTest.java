/*
 * Copyright 2014-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class MemberIdTest {

  @Test
  void shouldFormatIdWithoutZoneWhenZoneIsNull() {
    // given / when
    final var memberId = MemberId.from(null, 7);

    // then
    assertThat(memberId.id()).isEqualTo("7");
  }

  @Test
  void shouldFormatIdWithZonePrefixWhenZoneIsSet() {
    // given / when
    final var memberId = MemberId.from("us-east", 7);

    // then
    assertThat(memberId.id()).isEqualTo("us-east/7");
  }

  @Test
  void shouldExtractNodeIdFromBareIntegerForm() {
    // given
    final var memberId = MemberId.from("3");

    // when
    final var nodeId = MemberId.extractNodeId(memberId);

    // then
    assertThat(nodeId).isEqualTo(3);
  }

  @Test
  void shouldExtractNodeIdFromZonedForm() {
    // given
    final var memberId = MemberId.from("us-east/12");

    // when
    final var nodeId = MemberId.extractNodeId(memberId);

    // then
    assertThat(nodeId).isEqualTo(12);
  }

  @Test
  void shouldThrowWhenIdHasNoNumericSuffix() {
    // given
    final var memberId = MemberId.from("anonymous");

    // then
    assertThatThrownBy(() -> MemberId.extractNodeId(memberId))
        .isInstanceOf(NumberFormatException.class);
  }
}
