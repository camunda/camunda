/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VersionedMemberIdTest {

  @Test
  void shouldCreateVersionedMemberId() {
    // when
    final var memberId = new VersionedMemberId("0", 1);

    // then
    assertThat(memberId.id()).isEqualTo("0");
    assertThat(memberId.getIdVersion()).isEqualTo(1);
  }

  @Test
  void shouldRejectZeroVersion() {
    // when/then
    assertThatThrownBy(() -> new VersionedMemberId("0", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idVersion must be > 0");
  }

  @Test
  void shouldRejectNegativeVersion() {
    // when/then
    assertThatThrownBy(() -> new VersionedMemberId("0", -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idVersion must be > 0");
  }

  @Test
  void shouldBeEqualWhenSameIdAndVersion() {
    // given
    final var memberId1 = new VersionedMemberId("0", 1);
    final var memberId2 = new VersionedMemberId("0", 1);

    // then
    assertThat(memberId1).isEqualTo(memberId2);
    assertThat(memberId1.hashCode()).isEqualTo(memberId2.hashCode());
  }

  @Test
  void shouldNotBeEqualWhenDifferentVersion() {
    // given
    final var memberId1 = new VersionedMemberId("0", 1);
    final var memberId2 = new VersionedMemberId("0", 2);

    // then
    assertThat(memberId1).isNotEqualTo(memberId2);
  }

  @Test
  void shouldNotBeEqualWhenDifferentId() {
    // given
    final var memberId1 = new VersionedMemberId("0", 1);
    final var memberId2 = new VersionedMemberId("1", 1);

    // then
    assertThat(memberId1).isNotEqualTo(memberId2);
  }

  @Test
  void shouldBeEqualToPlainMemberId() {
    // given
    final var versionedMemberId = new VersionedMemberId("0", 1);
    final var plainMemberId = MemberId.from("0");

    // then - versioned member ID is equal to plain member ID
    assertThat(versionedMemberId).isEqualTo(plainMemberId);
  }

  @Test
  void shouldNotBeEqualToPlainMemberIdWithDifferentId() {
    // given
    final var versionedMemberId = new VersionedMemberId("0", 1);
    final var plainMemberId = MemberId.from("1");

    // then - versioned member ID is NOT equal to plain member ID
    assertThat(versionedMemberId).isNotEqualTo(plainMemberId);
  }

  @Test
  void shouldIncludeVersionInToString() {
    // given
    final var memberId = new VersionedMemberId("0", 42);

    // then
    assertThat(memberId.toString()).isEqualTo("0@v42");
  }

  @Test
  void shouldNotIncludeVersionInToStringIfZero() {
    // when
    final var memberId = MemberId.from(456, 0);

    // then
    assertThat(memberId).returns("456", MemberId::id).returns("456", MemberId::toString);
  }

  // Tests for MemberId.from(int, long) factory method

  @Test
  void factoryMethodShouldCreatePlainMemberIdWhenVersionIsZero() {
    // when
    final var memberId = MemberId.from(5, 0);

    // then
    assertThat(memberId)
        .isNotInstanceOf(VersionedMemberId.class)
        .returns("5", MemberId::id)
        .returns(0L, MemberId::getIdVersion);
  }

  @Test
  void factoryMethodShouldCreateVersionedMemberIdWhenVersionIsPositive() {
    // when
    final var memberId = MemberId.from(3, 42);

    // then
    assertThat(memberId)
        .isInstanceOf(VersionedMemberId.class)
        .returns("3", MemberId::id)
        .returns(42L, MemberId::getIdVersion);
  }

  // Tests for HashMap lookup compatibility between VersionedMemberId and MemberId

  static Stream<Arguments> hashMapLookupTestCases() {
    return Stream.of(
        // stored key, lookup key, expected found, description
        Arguments.of(
            new VersionedMemberId("0", 1),
            MemberId.from("0"),
            true,
            "plain MemberId finds VersionedMemberId with same node id"),
        Arguments.of(
            MemberId.from("0"),
            new VersionedMemberId("0", 1),
            true,
            "VersionedMemberId finds plain MemberId with same node id"),
        Arguments.of(
            new VersionedMemberId("0", 1),
            new VersionedMemberId("0", 2),
            false,
            "VersionedMemberId with different version does not match"),
        Arguments.of(
            new VersionedMemberId("0", 1),
            MemberId.from("1"),
            false,
            "MemberId with different node id does not match"),
        Arguments.of(
            MemberId.from("0"),
            MemberId.from("1"),
            false,
            "plain MemberIds with different node ids do not match"));
  }

  @ParameterizedTest(name = "{3}")
  @MethodSource("hashMapLookupTestCases")
  void shouldHandleHashMapLookupCorrectly(
      final MemberId storedKey,
      final MemberId lookupKey,
      final boolean expectedFound,
      final String description) {
    // given
    final var map = new HashMap<MemberId, String>();
    map.put(storedKey, "value");

    // when
    final var result = map.get(lookupKey);

    // then
    if (expectedFound) {
      assertThat(result).as(description).isEqualTo("value");
      assertThat(map.containsKey(lookupKey)).as(description + " (containsKey)").isTrue();
    } else {
      assertThat(result).as(description).isNull();
      assertThat(map.containsKey(lookupKey)).as(description + " (containsKey)").isFalse();
    }
  }
}
