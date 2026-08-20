/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.impl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.snapshot.InMemorySnapshot;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.utils.concurrent.ThreadContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

final class LogCompactorTest {
  private ThreadContext threadContext;
  private RaftLog raftLog;
  private LogCompactor compactor;
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void beforeEach() {
    threadContext = Mockito.mock(ThreadContext.class);
    raftLog = Mockito.mock(RaftLog.class);
    // immediately run anything to be executed
    Mockito.doAnswer(
            i -> {
              i.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(threadContext)
        .execute(Mockito.any());

    compactor =
        new LogCompactor(threadContext, raftLog, 5, new RaftServiceMetrics("1", meterRegistry));
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

  @Test
  void shouldCompactBasedOnOldestSnapshot() {
    // given
    final var store = new TestSnapshotStore(new AtomicReference<>());
    InMemorySnapshot.newPersistedSnapshot(0, 10L, 1, 30, store).reserve();
    InMemorySnapshot.newPersistedSnapshot(0, 30L, 1, 30, store);

    // when
    compactor.compactFromSnapshots(store);

    // then
    Mockito.verify(
            raftLog,
            Mockito.times(1)
                .description(
                    "should compact up to lowest snapshot index, minus replication threshold"))
        .deleteUntil(Mockito.eq(5L));
  }

  private static Stream<Named<Consumer<LogCompactor>>> provideCompactors() {
    return Stream.of(
        Named.of("#compact", LogCompactor::compact),
        Named.of(
            "#compactIgnoringReplicationThreshold",
            LogCompactor::compactIgnoringReplicationThreshold));
  }
}
