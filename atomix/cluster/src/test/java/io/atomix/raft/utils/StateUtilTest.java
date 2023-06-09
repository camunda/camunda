/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StateUtilTest {

  private static final Logger LOG = LoggerFactory.getLogger("TEST");

  @ParameterizedTest
  @MethodSource("provideInconsistentState")
  void shouldThrowException(
      final long snapshotIndex, final long firstIndex, final boolean isLogEmpty) {
    assertThatException()
        .isThrownBy(
            () ->
                StateUtil.verifySnapshotLogConsistent(
                    1L, snapshotIndex, firstIndex, isLogEmpty, i -> {}, LOG));
  }

  @ParameterizedTest
  @MethodSource("provideConsistentStateWithNoNeedToResetLog")
  void shouldNotThrowException(
      final long snapshotIndex, final long firstIndex, final boolean isLogEmpty) {
    assertThatNoException()
        .isThrownBy(
            () ->
                StateUtil.verifySnapshotLogConsistent(
                    1L,
                    snapshotIndex,
                    firstIndex,
                    isLogEmpty,
                    i -> {
                      throw new RuntimeException("Expected not to call reset");
                    },
                    LOG));
  }

  @ParameterizedTest
  @MethodSource("provideStateThatAreConsistentAfterLogReset")
  void shouldResetLog(final long snapshotIndex, final long firstIndex, final boolean isLogEmpty) {
    final CompletableFuture<Long> logReset = new CompletableFuture<>();
    assertThatNoException()
        .isThrownBy(
            () ->
                StateUtil.verifySnapshotLogConsistent(
                    1L, snapshotIndex, firstIndex, isLogEmpty, logReset::complete, LOG));
    assertThat(logReset).succeedsWithin(Duration.ofMillis(100)).isEqualTo(snapshotIndex + 1);
  }

  private static Stream<Arguments> provideInconsistentState() {
    return Stream.of(
        Arguments.of(0, 5, false), Arguments.of(1, 5, false), Arguments.of(3, 6, false));
  }

  private static Stream<Arguments> provideConsistentStateWithNoNeedToResetLog() {
    // Valid states with no gaps between snapshot and log
    return Stream.of(
        Arguments.of(4, 5, true),
        Arguments.of(4, 5, false),
        Arguments.of(5, 5, false),
        Arguments.of(6, 5, false),
        Arguments.of(0, 1, true),
        Arguments.of(0, 1, false));
  }

  private static Stream<Arguments> provideStateThatAreConsistentAfterLogReset() {
    // There is a gap between snapshot and log, but log is empty. So the state is consistent after
    // log is reset to snapshotIndex + 1
    return Stream.of(Arguments.of(0, 5, true), Arguments.of(3, 5, true));
  }
}
