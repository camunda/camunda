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
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StateUtilTest {

  private static final Logger LOG = LoggerFactory.getLogger("TEST");

  @ParameterizedTest
  @MethodSource("provideInconsistentState")
  void shouldThrowException(final RaftState state) {
    assertThatException()
        .isThrownBy(
            () ->
                StateUtil.verifySnapshotLogConsistent(
                    1L,
                    state.snapshotIndex(),
                    state.firstIndex(),
                    state.isLogEmpty(),
                    i -> {},
                    LOG));
  }

  @ParameterizedTest
  @MethodSource("provideConsistentStateWithNoNeedToResetLog")
  void shouldNotThrowException(final RaftState state) {
    assertThatNoException()
        .isThrownBy(
            () ->
                StateUtil.verifySnapshotLogConsistent(
                    1L,
                    state.snapshotIndex(),
                    state.firstIndex(),
                    state.isLogEmpty(),
                    i -> {
                      throw new RuntimeException("Expected not to call reset");
                    },
                    LOG));
  }

  @ParameterizedTest
  @MethodSource("provideStateThatAreConsistentAfterLogReset")
  void shouldResetLog(final RaftState state) {
    final CompletableFuture<Long> logReset = new CompletableFuture<>();
    assertThatNoException()
        .isThrownBy(
            () ->
                StateUtil.verifySnapshotLogConsistent(
                    1L,
                    state.snapshotIndex(),
                    state.firstIndex(),
                    state.isLogEmpty(),
                    logReset::complete,
                    LOG));
    assertThat(logReset)
        .succeedsWithin(Duration.ofMillis(100))
        .isEqualTo(state.snapshotIndex() + 1);
  }

  private static Stream<Arguments> provideInconsistentState() {
    return Stream.of(
        Arguments.of(
            Named.of("No snapshot before the log's first entry.", RaftState.of(0, 5, false))),
        Arguments.of(
            Named.of(
                "Entries between snapshot and the first log entry are missing.(1)",
                RaftState.of(1, 5, false))),
        Arguments.of(
            Named.of(
                "Entries between snapshot and the first log entry are missing.(2)",
                RaftState.of(3, 6, false))));
  }

  private static Stream<Arguments> provideConsistentStateWithNoNeedToResetLog() {
    // Valid states with no gaps between snapshot and log
    return Stream.of(
        Arguments.of(
            Named.of(
                "Follower receives a new snapshot, and restarted before receiving log entries.",
                RaftState.of(4, 5, true))),
        Arguments.of(
            Named.of(
                "Follower receives a new snapshot, receives log entries and restarts",
                RaftState.of(4, 5, false))),
        Arguments.of(
            Named.of(
                "Any node after compacting the log after snapshotting, First index = snapshotIndex",
                RaftState.of(5, 5, false))),
        Arguments.of(
            Named.of(
                "Any node after compacting the log after snapshotting. First index < snapshotIndex",
                RaftState.of(6, 5, false))),
        Arguments.of(Named.of("Initial state", RaftState.of(0, 1, true))),
        Arguments.of(
            Named.of(
                "State after appending log entries, with out snapshot",
                RaftState.of(0, 1, false))));
  }

  private static Stream<Arguments> provideStateThatAreConsistentAfterLogReset() {
    // There is a gap between snapshot and log, but log is empty. So the state is consistent after
    // log is reset to snapshotIndex + 1
    return Stream.of(
        Arguments.of(
            Named.of(
                "Follower received first snapshot, reset the log, crash before committing snapshot.",
                RaftState.of(0, 5, true))),
        Arguments.of(
            Named.of(
                "Follower received a newer snapshot, reset the logs, crash before committing snapshot.",
                RaftState.of(3, 5, true))));
  }

  private static record RaftState(long snapshotIndex, long firstIndex, boolean isLogEmpty) {
    static RaftState of(final long snapshotIndex, final long firstIndex, final boolean isLogEmpty) {
      return new RaftState(snapshotIndex, firstIndex, isLogEmpty);
    }
  }
}
