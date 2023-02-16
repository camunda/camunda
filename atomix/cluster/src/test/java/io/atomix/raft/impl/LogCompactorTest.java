/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.impl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

final class LogCompactorTest {
  private ThreadContext threadContext;
  private RaftLog raftLog;
  private LogCompactor compactor;

  @BeforeEach
  void beforeEach() {
    threadContext = Mockito.mock(ThreadContext.class);
    raftLog = Mockito.mock(RaftLog.class);

    compactor =
        new LogCompactor(
            threadContext,
            raftLog,
            5,
            new RaftServiceMetrics("1"),
            LoggerFactory.getLogger(getClass()));
  }

  @Test
  void shouldCompact() {
    // given
    compactor.setCompactableIndex(12);

    // when
    compactor.compact();

    // then - should have compacted the log up to the compactable index - the replication threshold
    Mockito.verify(
            raftLog,
            Mockito.times(1)
                .description("should compact up to index minus the replication threshold"))
        .deleteUntil(7);
  }

  @Test
  void shouldCompactIgnoringThreshold() {
    // given
    compactor.setCompactableIndex(12);

    // when
    compactor.compactIgnoringReplicationThreshold();

    // then
    Mockito.verify(raftLog, Mockito.times(1).description("should compact until compactable index"))
        .deleteUntil(12);
  }

  @ParameterizedTest
  @MethodSource("provideCompactors")
  void shouldNotCompactOnDifferentThread(final Consumer<LogCompactor> compactMethod) {
    // given
    Mockito.doThrow(new IllegalStateException("Invalid thread")).when(threadContext).checkThread();
    compactor.setCompactableIndex(12);

    // when
    assertThatCode(() -> compactMethod.accept(compactor)).isInstanceOf(IllegalStateException.class);
  }

  private static Stream<Named<Consumer<LogCompactor>>> provideCompactors() {
    return Stream.of(
        Named.of("#compact", LogCompactor::compact),
        Named.of(
            "#compactIgnoringReplicationThreshold",
            LogCompactor::compactIgnoringReplicationThreshold));
  }
}
