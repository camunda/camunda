/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class MemberIdTest {

  @Test
  void shouldFormatIdWithoutZoneWhenZoneIsNull() {
    // given / when
    final var memberId = MemberId.from(null, 7);

    // then
    assertThat(memberId.id()).isEqualTo("7");
  }

  @Test
  void shouldThrowWhenZoneIsEmpty() {
    // given / when / then
    assertThatThrownBy(() -> MemberId.from("", 7)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowWhenZoneIsBlank() {
    // given / when / then
    assertThatThrownBy(() -> MemberId.from("   ", 7)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldStripSurroundingWhitespaceFromZone() {
    // given / when
    final var memberId = MemberId.from("  eu-west  ", 7);

    // then
    assertThat(memberId.id()).isEqualTo("eu-west/7");
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
    final var nodeId = memberId.nodeIdx();

    // then
    assertThat(nodeId).isEqualTo(3);
  }

  @Test
  void shouldExtractNodeIdFromZonedForm() {
    // given
    final var memberId = MemberId.from("us-east/12");

    // when
    final var nodeId = memberId.nodeIdx();

    // then
    assertThat(nodeId).isEqualTo(12);
  }

  @Test
  void shouldThrowWhenIdHasNoNumericSuffix() {
    // given / when / then
    assertThatThrownBy(() -> MemberId.from("anonymous")).isInstanceOf(NumberFormatException.class);
  }

  static Stream<Arguments> isInZoneCases() {
    return Stream.of(
        Arguments.of("us-east/7", "us-east", true), // matching zone
        Arguments.of("us-east/7", "eu-west", false), // different zone
        Arguments.of("7", "us-east", false), // zone set but id is bare
        Arguments.of("7", null, true), // null zone, bare id
        Arguments.of("us-east/1", null, false) // null zone, zoned id
        );
  }

  @ParameterizedTest
  @MethodSource("isInZoneCases")
  void shouldCheckIsInZone(final String id, final String zone, final boolean expected) {
    // given / when / then
    assertThat(MemberId.from(id).isInZone(zone)).isEqualTo(expected);
  }

  @Test
  void shouldThrowWhenMemberZoneDoesNotMatchMemberIdPrefix() {
    // given
    final var memberId = MemberId.from("us-east/0");

    // then
    assertThatThrownBy(() -> Member.builder(memberId).withZoneId("us").build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
