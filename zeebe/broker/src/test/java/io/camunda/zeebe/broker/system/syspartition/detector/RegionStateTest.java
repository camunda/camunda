/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector;

import static io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState.DEAD;
import static io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState.HEALTHY;
import static io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState.SUSPECT;
import static io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class RegionStateTest {

  static List<Arguments> memberStates() {
    return List.of(
        Arguments.of(List.of(HEALTHY, HEALTHY), RegionState.ACTIVE),
        Arguments.of(List.of(HEALTHY, SUSPECT), RegionState.SUSPECT),
        Arguments.of(List.of(HEALTHY, DEAD), RegionState.SUSPECT),
        Arguments.of(List.of(SUSPECT, DEAD), RegionState.SUSPECT),
        Arguments.of(List.of(UNKNOWN, DEAD), RegionState.SUSPECT),
        Arguments.of(List.of(DEAD, DEAD), RegionState.FAILED),
        Arguments.of(List.of(HEALTHY, UNKNOWN), RegionState.UNKNOWN),
        Arguments.of(List.of(UNKNOWN, UNKNOWN), RegionState.UNKNOWN),
        Arguments.of(List.of(), RegionState.UNKNOWN));
  }

  @ParameterizedTest
  @MethodSource("memberStates")
  void shouldAggregateMemberStates(
      final List<MemberLivenessState> memberStates, final RegionState expected) {
    // when
    final var state = RegionState.of(memberStates);

    // then
    assertThat(state).isEqualTo(expected);
  }
}
