/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.cluster.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class RaftMemberContextTest {

  @Test
  void shouldStartWithZeroLogReplicationLagBytes() {
    // given
    final var context = newContext();

    // then
    assertThat(context.getLogReplicationLagBytes()).isZero();
    assertThat(context.getSnapshotInstallRemainingBytes()).isZero();
  }

  @Test
  void shouldIncrementLogReplicationLagBytesOnRecordAppend() {
    // given
    final var context = newContext();

    // when
    context.recordAppend(250);
    context.recordAppend(100);

    // then
    assertThat(context.getLogReplicationLagBytes()).isEqualTo(350);
  }

  @Test
  void shouldDecrementLogReplicationLagBytesOnRecordAck() {
    // given
    final var context = newContext();
    context.recordAppend(500);

    // when
    context.recordAck(200);

    // then
    assertThat(context.getLogReplicationLagBytes()).isEqualTo(300);
  }

  @Test
  void shouldCalibrateLogReplicationLagBytesOnResetFromReaderPosition() {
    // given
    final var reader = mock(RaftLogReader.class);
    when(reader.hasNext()).thenReturn(false);
    when(reader.seek(anyLong())).thenAnswer(invocation -> invocation.getArgument(0));
    when(reader.bytesUntilEnd()).thenReturn(4321L);

    final var log = mock(RaftLog.class);
    when(log.openUncommittedReader()).thenReturn(reader);

    final var context = newContext();
    context.openReplicationContext(log);

    // when - seed a non-zero value, then reset
    context.recordAppend(99);
    context.reset(100);

    // then - reset recalibrates from reader.bytesUntilEnd(), wiping the seeded value
    assertThat(context.getLogReplicationLagBytes()).isEqualTo(4321L);
  }

  @Test
  void shouldSetAndSubtractSnapshotInstallRemainingBytes() {
    // given
    final var context = newContext();

    // when
    context.setSnapshotInstallRemainingBytes(1000);
    context.subtractSnapshotInstallRemainingBytes(300);

    // then
    assertThat(context.getSnapshotInstallRemainingBytes()).isEqualTo(700);
  }

  @Test
  void shouldFloorSnapshotInstallRemainingBytesAtZero() {
    // given
    final var context = newContext();
    context.setSnapshotInstallRemainingBytes(100);

    // when - subtract more than what's left
    context.subtractSnapshotInstallRemainingBytes(500);

    // then
    assertThat(context.getSnapshotInstallRemainingBytes()).isZero();
  }

  @Test
  void shouldClearReplicationStateOnResetState() {
    // given
    final var log = mock(RaftLog.class);
    final var context = newContext();
    context.recordAppend(1234);
    context.setSnapshotInstallRemainingBytes(5678);

    // when
    context.resetState(log);

    // then
    assertThat(context.getLogReplicationLagBytes()).isZero();
    assertThat(context.getSnapshotInstallRemainingBytes()).isZero();
  }

  private RaftMemberContext newContext() {
    final var member = new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.now());
    return new RaftMemberContext(member, mock(RaftClusterContext.class), 1);
  }
}
