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

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class MemberIdTest {

  @Test
  void shouldFormatIdWithoutZoneWhenZoneIsNull() {
    // given / when
    final var memberId = MemberId.from(7);

    // then
    assertThat(memberId)
        .returns(7, MemberId::nodeIdx)
        .returns(null, MemberId::zone)
        .returns("7", MemberId::id);
    assertEncodeDecode(memberId);
  }

  static Stream<Named<String>> illegalZones() {
    return Stream.of(
        Named.of("empty", ""),
        Named.of("blank", "   "),
        Named.of("contains underscore", "zone_a"),
        Named.of("contains slash", "zone/a"),
        Named.of("contains dot", "zone.a"),
        Named.of("starts with hyphen", "-zone"),
        Named.of("exceeds max length", "a".repeat(64)),
        Named.of("contains whitespace", "  eu-west  "));
  }

  @ParameterizedTest
  @MethodSource("illegalZones")
  void shouldThrowWhenZoneIsIllegal(final String zone) {
    // given / when / then
    assertThatThrownBy(() -> MemberId.from(zone, 7)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAcceptZoneAtMaxLength() {
    // given
    final var zone = "a".repeat(63);

    // when / then
    assertThatNoException().isThrownBy(() -> MemberId.from(zone, 7));
  }

  @Test
  void shouldAcceptZoneWithAlphanumericAndHyphens() {
    // given / when
    final var memberId = MemberId.from("eu-west-1", 7);

    // then
    assertThat(memberId).returns("eu-west-1", MemberId::zone);
  }

  @Test
  void shouldFormatIdWithZonePrefixWhenZoneIsSet() {
    // given / when
    final var memberId = MemberId.from("us-east", 7);

    // then
    assertThat(memberId)
        .returns(7, MemberId::nodeIdx)
        .returns("us-east", MemberId::zone)
        .returns("us-east_7", MemberId::id);
    assertEncodeDecode(memberId);
  }

  @Test
  void shouldExtractNodeIdFromBareIntegerForm() {
    // given
    final var memberId = MemberId.from("3");

    // when / then
    assertThat(memberId).returns(3, MemberId::nodeIdx).returns(null, MemberId::zone);
    assertEncodeDecode(memberId);
  }

  @Test
  void shouldExtractComponentsFromZonedForm() {
    // given
    final var memberId = MemberId.from("us-east_12");

    // when / then
    assertThat(memberId)
        .returns(12, MemberId::nodeIdx)
        .returns("us-east", MemberId::zone)
        .returns("us-east_12", MemberId::id);
    assertEncodeDecode(memberId);
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

  @Test
  void shouldNotClassifyTrailingUnderscoreAsZonedId() {
    // given — "eu-west_" looks like a zoned form but has no nodeIdx
    final var memberId = MemberId.from("eu-west_");

    // when / then — treated as bare non-integer id, not a zoned id
    assertThat(memberId).returns(null, MemberId::zone);
    assertThatThrownBy(memberId::nodeIdx).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldNotClassifyNonNumericSuffixAsZonedId() {
    // given — "eu-west_abc" has an underscore but the suffix is not a number
    final var memberId = MemberId.from("eu-west_abc");

    // when / then — treated as bare non-integer id, not a zoned id
    assertThat(memberId).returns(null, MemberId::zone);
    assertThatThrownBy(memberId::nodeIdx).isInstanceOf(IllegalStateException.class);
  }

  static Stream<Arguments> isInZoneCases() {
    return Stream.of(
        Arguments.of("us-east_7", "us-east", true), // matching zone
        Arguments.of("us-east_7", "eu-west", false), // different zone
        Arguments.of("7", "us-east", false), // zone set but id is bare
        Arguments.of("7", null, true), // null zone, bare id
        Arguments.of("us-east_1", null, false) // null zone, zoned id
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
    final var memberId = MemberId.from("us-east_0");

    // then
    assertThatThrownBy(() -> Member.builder(memberId).withZoneId("us").build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertEncodeDecode(final MemberId memberId) {
    final var decoded = MemberId.from(memberId.id());
    assertThat(decoded).isEqualTo(memberId).returns(memberId.hashCode(), MemberId::hashCode);
  }

  @Nested
  class IdComparatorTest {

    @Test
    void shouldOrderNumericallyNotLexicographically() {
      // given — lexicographic order would give "10" < "2", but numerical gives "2" < "10"
      final var member2 = MemberId.from("2");
      final var member10 = MemberId.from("10");

      // when / then
      assertThat(MemberId.ID_COMPARATOR.compare(member2, member10)).isNegative();
      assertThat(MemberId.ID_COMPARATOR.compare(member10, member2)).isPositive();
    }

    @Test
    void shouldOrderZoneAwareMembersByNodeIdxFirst() {
      // given — us_0 has nodeIdx=0, eu_1 has nodeIdx=1; nodeIdx wins regardless of zone
      final var usZone0 = MemberId.from("us", 0);
      final var euZone1 = MemberId.from("eu", 1);

      // when / then
      assertThat(MemberId.ID_COMPARATOR.compare(usZone0, euZone1)).isNegative();
    }

    @Test
    void shouldUseZoneAsSecondaryKeyWhenNodeIdxTies() {
      // given — eu_0 and us_0 both have nodeIdx=0; zone breaks the tie alphabetically
      final var euZone0 = MemberId.from("eu", 0);
      final var usZone0 = MemberId.from("us", 0);

      // when / then — "eu" < "us"
      assertThat(MemberId.ID_COMPARATOR.compare(euZone0, usZone0)).isNegative();
      assertThat(MemberId.ID_COMPARATOR.compare(usZone0, euZone0)).isPositive();
    }

    @Test
    void shouldOrderNonZonedBeforeZonedWhenNodeIdxTies() {
      // given — null zone (non-zoned) sorts before any named zone (nullsFirst)
      final var bare = MemberId.from("0");
      final var zoned = MemberId.from("eu", 0);

      // when / then
      assertThat(MemberId.ID_COMPARATOR.compare(bare, zoned)).isNegative();
    }

    @Test
    void shouldOrderAnonymousMembersAfterIndexedMembers() {
      // given — anonymous members have no nodeIdx and sort last (nullsLast)
      final var indexed = MemberId.from("0");
      final var anonymous = MemberId.anonymous();

      // when / then
      assertThat(MemberId.ID_COMPARATOR.compare(indexed, anonymous)).isNegative();
    }

    @Test
    void shouldProduceStableTotalOrderForMixedCluster() {
      // given — a mix of zoned and non-zoned members with various nodeIdx values
      final var bare0 = MemberId.from("0");
      final var bare2 = MemberId.from("2");
      final var bare10 = MemberId.from("10");
      final var eu0 = MemberId.from("eu", 0);
      final var us0 = MemberId.from("us", 0);
      final var eu1 = MemberId.from("eu", 1);

      final var members = List.of(bare10, us0, eu1, bare2, eu0, bare0);

      // when
      final var sorted = members.stream().sorted(MemberId.ID_COMPARATOR).toList();

      // then — bare0 first (nodeIdx=0, no zone), then eu0 (nodeIdx=0, "eu"), then us0 (nodeIdx=0,
      // "us"), then eu1, bare2, bare10
      assertThat(sorted).containsExactly(bare0, eu0, us0, eu1, bare2, bare10);
    }
  }
}
