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
import static org.assertj.core.api.Assertions.assertThatNoException;
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

    assertThat(memberId)
        .returns(7, MemberId::nodeIdx)
        .returns(null, MemberId::zone)
        .returns("7", MemberId::id);
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
    assertThat(memberId)
        .returns(7, MemberId::nodeIdx)
        .returns("eu-west", MemberId::zone)
        .returns("eu-west/7", MemberId::id);
  }

  @Test
  void shouldFormatIdWithZonePrefixWhenZoneIsSet() {
    // given / when
    final var memberId = MemberId.from("us-east", 7);

    // then
    assertThat(memberId)
        .returns(7, MemberId::nodeIdx)
        .returns("us-east", MemberId::zone)
        .returns("us-east/7", MemberId::id);
  }

  @Test
  void shouldExtractNodeIdFromBareIntegerForm() {
    // given
    final var memberId = MemberId.from("3");

    // when / then
    assertThat(memberId).returns(3, MemberId::nodeIdx).returns(null, MemberId::zone);
  }

  @Test
  void shouldExtractComponentsFromZonedForm() {
    // given
    final var memberId = MemberId.from("us-east/12");

    // when / then
    assertThat(memberId)
        .returns(12, MemberId::nodeIdx)
        .returns("us-east", MemberId::zone)
        .returns("us-east/12", MemberId::id);
  }

  @Test
  void shouldThrowWhenIdHasNoNumericSuffix() {
    // given
    final var memberId = MemberId.from("anonymous");

    // when / then
    assertThatThrownBy(memberId::nodeIdx).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldNotThrowForAnonymous() {
    // given / when / then
    assertThatNoException().isThrownBy(MemberId::anonymous);
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
