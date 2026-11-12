/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class LeaderCommitGateTest {

  private static final long TERM = 3;

  private final RaftPartitionServer raft = mock(RaftPartitionServer.class);
  private final LogStream logStream = mock(LogStream.class);
  private final LogStreamReader reader = mock(LogStreamReader.class);
  private final AtomicInteger notified = new AtomicInteger();
  private LeaderCommitGate gate;

  @BeforeEach
  void setUp() {
    when(logStream.newLogStreamReader()).thenReturn(reader);
    when(reader.seekToEnd()).thenReturn(10L);
    gate = new LeaderCommitGate(TERM, raft, logStream);
    gate.addCommitListener(notified::incrementAndGet);
  }

  @Test
  void shouldListenToRaft() {
    // then
    verify(raft).addCommittedEntryListener(gate);
    verify(raft).addRoleChangeListener(gate);
  }

  @Test
  void shouldNotMoveBeforeRaftReportsThatItLeadsInTheTerm() {
    // when
    gate.onCommit(5);

    // then
    assertThat(gate.committedPosition()).isEqualTo(-1);
    assertThat(gate.stopped()).isFalse();
    assertThat(notified).hasValue(0);
  }

  @Test
  void shouldStartWhereTheCommittedLogEndsOnceRaftLeadsInTheTerm() {
    // when
    gate.onNewRole(Role.LEADER, TERM);

    // then
    assertThat(gate.committedPosition()).isEqualTo(10);
    assertThat(gate.stopped()).isFalse();
    assertThat(notified).hasValue(1);
    verify(reader).close();
  }

  @Test
  void shouldStartAtZeroWhenTheLogIsEmpty() {
    // given
    when(reader.seekToEnd()).thenReturn(-1L);

    // when
    gate.onNewRole(Role.LEADER, TERM);

    // then
    assertThat(gate.committedPosition()).isZero();
  }

  @Test
  void shouldMoveForwardWithEachCommitWhileLeading() {
    // given
    gate.onNewRole(Role.LEADER, TERM);

    // when
    gate.onCommit(12);
    gate.onCommit(11);

    // then
    assertThat(gate.committedPosition()).isEqualTo(12);
    assertThat(notified).hasValue(2);
  }

  @Test
  void shouldStopOnceRaftNoLongerLeads() {
    // given
    gate.onNewRole(Role.LEADER, TERM);

    // when
    gate.onNewRole(Role.FOLLOWER, TERM);

    // then
    assertThat(gate.stopped()).isTrue();
    assertThat(notified).hasValue(2);
    verify(raft).removeRoleChangeListener(gate);
    verify(raft).removeCommittedEntryListener(gate);
  }

  @Test
  void shouldStopOnceRaftLeadsInAnotherTerm() {
    // given
    gate.onNewRole(Role.LEADER, TERM);

    // when
    gate.onNewRole(Role.LEADER, TERM + 1);

    // then
    assertThat(gate.stopped()).isTrue();
  }

  @Test
  void shouldStopWithoutStartingWhenRaftReportsAnotherRoleFirst() {
    // when
    gate.onNewRole(Role.FOLLOWER, TERM);

    // then
    assertThat(gate.stopped()).isTrue();
    assertThat(gate.committedPosition()).isEqualTo(-1);
    assertThat(notified).hasValue(1);
  }

  @Test
  void shouldNotMoveOnceStopped() {
    // given
    gate.onNewRole(Role.LEADER, TERM);
    gate.onNewRole(Role.FOLLOWER, TERM);

    // when - a later leader's commits, or a stale report that it leads
    gate.onCommit(20);
    gate.onNewRole(Role.LEADER, TERM);

    // then
    assertThat(gate.committedPosition()).isEqualTo(10);
    assertThat(gate.stopped()).isTrue();
    assertThat(notified).hasValue(2);
  }

  @Test
  void shouldWaitForTheNextCommitWhenItCannotReadTheLog() {
    // given
    when(logStream.newLogStreamReader()).thenThrow(new IllegalStateException("log stream closed"));

    // when
    assertThatNoException().isThrownBy(() -> gate.onNewRole(Role.LEADER, TERM));
    final long beforeCommit = gate.committedPosition();
    gate.onCommit(15);

    // then
    assertThat(beforeCommit).isEqualTo(-1);
    assertThat(gate.committedPosition()).isEqualTo(15);
    assertThat(gate.stopped()).isFalse();
  }

  @Test
  void shouldTellEveryListenerEvenIfOneFails() {
    // given
    final var failing = new AtomicInteger();
    final var otherGate = new LeaderCommitGate(TERM, raft, logStream);
    otherGate.addCommitListener(
        () -> {
          failing.incrementAndGet();
          throw new IllegalStateException("listener failed");
        });
    otherGate.addCommitListener(notified::incrementAndGet);

    // when
    assertThatNoException().isThrownBy(() -> otherGate.onNewRole(Role.LEADER, TERM));

    // then
    assertThat(failing).hasValue(1);
    assertThat(notified).hasValue(1);
  }

  @Test
  void shouldNoLongerTellARemovedListener() {
    // given
    final Runnable listener = notified::incrementAndGet;
    gate.addCommitListener(listener);
    gate.removeCommitListener(listener);

    // when
    gate.onNewRole(Role.LEADER, TERM);

    // then - only the listener from the setup
    assertThat(notified).hasValue(1);
  }

  @Test
  void shouldStopOnceClosed() {
    // given
    gate.onNewRole(Role.LEADER, TERM);

    // when
    gate.close();
    gate.close();

    // then
    assertThat(gate.stopped()).isTrue();
    assertThat(notified).hasValue(2);
    verify(raft).removeRoleChangeListener(gate);
    verify(raft).removeCommittedEntryListener(gate);
  }

  @Test
  void shouldReportTheLastPositionWrittenToTheLog() {
    // given
    when(logStream.getLastWrittenPosition()).thenReturn(42L);

    // when
    final long lastWrittenPosition = gate.lastWrittenPosition();

    // then
    assertThat(lastWrittenPosition).isEqualTo(42);
  }
}
