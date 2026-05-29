/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class MemberIdUtilTest {

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

  static Stream<Named<String>> validMemberIds() {
    return Stream.of(Named.of("zoned", "us-east1_2"), Named.of("zoned", "2"));
  }

  @ParameterizedTest
  @MethodSource("illegalZones")
  void shouldThrowWhenZoneIsIllegalValidatingZone(final String zone) {
    // given / when / then
    assertThatThrownBy(() -> MemberIdUtil.validateZone(zone))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("illegalZones")
  void shouldThrowWhenZoneIsIllegalRegex(final String zone) {
    // given / when / then
    assertThat(MemberIdUtil.regex().matcher(zone).matches()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("validMemberIds")
  void shouldMatchMemberIdWithRegex(final String memberId) {
    final var matcher = MemberIdUtil.regex().matcher(memberId);
    assertThat(matcher.matches()).isTrue();
  }
}
