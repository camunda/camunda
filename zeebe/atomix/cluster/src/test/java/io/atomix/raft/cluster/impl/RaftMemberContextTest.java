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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.nio.ByteBuffer;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class RaftMemberContextTest {

  @Test
  void shouldStartWithZeroLogReplicationLag() {
    // given / then
    assertThat(newContext().getLogReplicationLag()).isZero();
  }

  @Test
  void shouldIncrementLogLagOnRecordAppend() {
    // given
    final var context = newContext();

    // when
    context.recordAppendedBytes(250);
    context.recordAppendedBytes(100);

    // then
    assertThat(context.getLogReplicationLag()).isEqualTo(350);
  }

  @Test
  void shouldSubtractInFlightBatchBytesOnAcknowledgement() {
    // given
    final var context = newContext();
    context.recordAppendedBytes(500);
    final long appendWatermark = context.recordInFlightAppend(200);

    // when
    context.acknowledgeInFlightAppends(appendWatermark);

    // then
    assertThat(context.getLogReplicationLag()).isEqualTo(300);
  }

  @Test
  void shouldNotSubtractTwiceWhenAcknowledgingSameWatermark() {
    // given
    final var context = newContext();
    context.recordAppendedBytes(500);
    final long appendWatermark = context.recordInFlightAppend(200);
    context.acknowledgeInFlightAppends(appendWatermark);

    // when
    context.acknowledgeInFlightAppends(appendWatermark);

    // then
    assertThat(context.getLogReplicationLag()).isEqualTo(300);
  }

  @Test
  void shouldAcknowledgeTimedOutBatchWhenALaterBatchSucceeds() {
    // given
    final var context = newContext();
    context.recordAppendedBytes(350);
    final long batchAWatermark = context.recordInFlightAppend(200);
    final long batchBWatermark = context.recordInFlightAppend(150);

    // when
    context.acknowledgeInFlightAppends(batchBWatermark);

    // then
    assertThat(context.getLogReplicationLag()).isZero();

    // when
    context.acknowledgeInFlightAppends(batchAWatermark);

    // then
    assertThat(context.getLogReplicationLag()).isZero();
  }

  @Test
  void shouldRecalibrateLogLagFromReaderOnReset() {
    // given
    final var reader = mock(RaftLogReader.class);
    when(reader.hasNext()).thenReturn(false);
    when(reader.seek(anyLong())).thenAnswer(invocation -> invocation.getArgument(0));
    when(reader.bytesUntilEnd()).thenReturn(4321L);
    final var log = mock(RaftLog.class);
    when(log.openUncommittedReader()).thenReturn(reader);

    final var context = newContext();
    context.openReplicationContext(log);
    context.recordAppendedBytes(99); // seed drift

    // when
    context.reset(100);

    // then
    assertThat(context.getLogReplicationLag()).isEqualTo(4321L);
  }

  @Test
  void shouldIgnoreAppendCallbackFromBeforeReset() {
    // given
    final var reader = mock(RaftLogReader.class);
    when(reader.hasNext()).thenReturn(false);
    when(reader.seek(anyLong())).thenAnswer(invocation -> invocation.getArgument(0));
    when(reader.bytesUntilEnd()).thenReturn(500L);
    final var log = mock(RaftLog.class);
    when(log.openUncommittedReader()).thenReturn(reader);

    final var context = newContext();
    context.openReplicationContext(log);
    context.recordAppendedBytes(200);
    final long appendWatermark = context.recordInFlightAppend(200);
    context.reset(100);

    // when
    context.acknowledgeInFlightAppends(appendWatermark);

    // then
    assertThat(context.getLogReplicationLag()).isEqualTo(500L);
  }

  @Test
  void shouldBeginSnapshotInstallWithoutMovingReplicationReader() {
    // given
    final var replicationReader = mock(RaftLogReader.class);
    final var lagReader = mock(RaftLogReader.class);
    final var currentEntry = mock(IndexedRaftLogEntry.class);
    when(replicationReader.hasNext()).thenReturn(true);
    when(replicationReader.next()).thenReturn(currentEntry);
    when(lagReader.bytesUntilEnd()).thenReturn(42L);
    final var log = mock(RaftLog.class);
    when(log.openUncommittedReader()).thenReturn(replicationReader, lagReader);
    final var snapshot = mock(PersistedSnapshot.class);
    when(snapshot.getIndex()).thenReturn(10L);
    when(snapshot.getTotalSizeInBytes()).thenReturn(1_000L);

    final var context = newContext();
    context.openReplicationContext(log);
    context.recordAppendedBytes(9_999);
    context.setNextSnapshotChunkId(ByteBuffer.allocate(1));
    final var replicationPosition = context.getCurrentEntry();

    // when
    context.beginSnapshotInstall(log, snapshot);

    // then
    assertThat(context.getNextSnapshotIndex()).isEqualTo(10L);
    assertThat(context.getNextSnapshotChunk()).isNull();
    assertThat(context.getSnapshotReplicationLag()).isEqualTo(1_000L);
    assertThat(context.getLogReplicationLag()).isEqualTo(42L);
    assertThat(context.getCurrentEntry()).isSameAs(replicationPosition);
    verify(lagReader).seek(11L);
    verify(lagReader).close();
    verify(replicationReader, never()).reset();
    verify(replicationReader, never()).seek(11L);
  }

  @Test
  void shouldIgnoreAppendAcknowledgementsFromBeforeSnapshotInstallBegins() {
    // given
    final var replicationReader = mock(RaftLogReader.class);
    final var lagReader = mock(RaftLogReader.class);
    when(lagReader.bytesUntilEnd()).thenReturn(42L);
    final var log = mock(RaftLog.class);
    when(log.openUncommittedReader()).thenReturn(replicationReader, lagReader);
    final var snapshot = mock(PersistedSnapshot.class);
    when(snapshot.getIndex()).thenReturn(10L);
    when(snapshot.getTotalSizeInBytes()).thenReturn(1_000L);

    final var context = newContext();
    context.openReplicationContext(log);
    final long staleWatermark = context.recordInFlightAppend(20L);

    // when
    context.beginSnapshotInstall(log, snapshot);
    context.acknowledgeInFlightAppends(staleWatermark);

    // then
    assertThat(context.getLogReplicationLag()).isEqualTo(42L);
  }

  @Test
  void shouldReportCombinedLogAndSnapshotLag() {
    // given
    final var context = newContext();

    // when
    context.recordAppendedBytes(700);
    context.setSnapshotReplicationLag(300);

    // then
    assertThat(context.getReplicationLagBytes()).isEqualTo(1000);
  }

  @Test
  void shouldClearBothCountersOnResetState() {
    // given
    final var log = mock(RaftLog.class);
    final var context = newContext();
    context.recordAppendedBytes(1234);
    context.setSnapshotReplicationLag(5678);

    // when
    context.resetState(log);

    // then
    assertThat(context.getLogReplicationLag()).isZero();
    assertThat(context.getSnapshotReplicationLag()).isZero();
    assertThat(context.getReplicationLagBytes()).isZero();
  }

  @Test
  void shouldStartWithZeroSnapshotReplicationLag() {
    // given
    final var context = newContext();

    // then
    assertThat(context.getSnapshotReplicationLag()).isZero();
    assertThat(context.getReplicationLagBytes()).isZero();
  }

  @Test
  void shouldSetAndSubtractSnapshotReplicationLag() {
    // given
    final var context = newContext();

    // when
    context.setSnapshotReplicationLag(1000);
    context.subtractSnapshotReplicationLag(300);

    // then
    assertThat(context.getSnapshotReplicationLag()).isEqualTo(700);
    assertThat(context.getReplicationLagBytes()).isEqualTo(700);
  }

  @Test
  void shouldFloorSnapshotReplicationLagAtZero() {
    // given
    final var context = newContext();
    context.setSnapshotReplicationLag(100);

    // when
    context.subtractSnapshotReplicationLag(500);

    // then
    assertThat(context.getSnapshotReplicationLag()).isZero();
    assertThat(context.getReplicationLagBytes()).isZero();
  }

  @Test
  void shouldRememberInFlightChunkBytes() {
    // given
    final var context = newContext();

    // when
    context.setSnapshotChunkBytesInFlight(512);

    // then
    assertThat(context.getSnapshotChunkBytesInFlight()).isEqualTo(512);
  }

  @Test
  void shouldClearSnapshotReplicationStateOnResetState() {
    // given
    final var log = mock(RaftLog.class);
    final var context = newContext();
    context.setSnapshotReplicationLag(5678);
    context.setSnapshotChunkBytesInFlight(512);

    // when
    context.resetState(log);

    // then
    assertThat(context.getSnapshotReplicationLag()).isZero();
    assertThat(context.getSnapshotChunkBytesInFlight()).isZero();
    assertThat(context.getReplicationLagBytes()).isZero();
  }

  private RaftMemberContext newContext() {
    final var member = new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.now());
    return new RaftMemberContext(member, mock(RaftClusterContext.class), 1);
  }
}
