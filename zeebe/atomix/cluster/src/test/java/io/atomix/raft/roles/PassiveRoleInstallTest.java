/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.impl.RaftClusterContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RaftReplicationMetrics;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.snapshot.impl.SnapshotChunkImpl;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A follower which does not need a snapshot tells the leader to stop sending it. Otherwise, the
 * leader sends the whole snapshot, and a follower which installed it would reset its log, deleting
 * entries after the snapshot index that it may have acknowledged.
 */
final class PassiveRoleInstallTest {
  private static final long TERM = 2;
  private static final long SNAPSHOT_INDEX = 10;
  private static final long SNAPSHOT_TERM = 1;
  private static final String SNAPSHOT_ID = "10-1-10-10-1";

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RaftContext raft = mock(RaftContext.class);
  private final RaftLog log = mock(RaftLog.class);
  private final RaftClusterContext cluster = mock(RaftClusterContext.class);
  private final ReceivableSnapshotStore snapshotStore = mock(ReceivableSnapshotStore.class);
  private final ReceivedSnapshot receivedSnapshot = mock(ReceivedSnapshot.class);
  private PassiveRole role;

  @BeforeEach
  void setup() {
    when(raft.getLog()).thenReturn(log);
    when(raft.getCluster()).thenReturn(cluster);
    when(raft.getStorage()).thenReturn(mock(RaftStorage.class));
    when(raft.getPersistedSnapshotStore()).thenReturn(snapshotStore);
    when(raft.getTerm()).thenReturn(TERM);
    when(raft.getReplicationMetrics()).thenReturn(mock(RaftReplicationMetrics.class));
    when(raft.getMeterRegistry()).thenReturn(meterRegistry);
    when(raft.getName()).thenReturn("partition-1");
    when(log.isEmpty()).thenReturn(true);
    when(snapshotStore.getLatestSnapshot()).thenReturn(Optional.empty());

    final var snapshotId = mock(SnapshotId.class);
    when(snapshotId.getSnapshotIdAsString()).thenReturn(SNAPSHOT_ID);
    when(receivedSnapshot.snapshotId()).thenReturn(snapshotId);
    when(receivedSnapshot.index()).thenReturn(SNAPSHOT_INDEX);
    when(receivedSnapshot.apply(any())).thenReturn(CompletableActorFuture.completed());
    when(receivedSnapshot.abort()).thenReturn(CompletableActorFuture.completed());
    when(receivedSnapshot.persist())
        .thenReturn(CompletableActorFuture.completed(mock(PersistedSnapshot.class)));
    when(snapshotStore.newReceivedSnapshot(SNAPSHOT_ID))
        .thenAnswer(ignored -> CompletableActorFuture.completed(receivedSnapshot));

    role = new PassiveRole(raft);
  }

  @Test
  void shouldSkipSnapshotWhenLogHasItsLastEntry() {
    // given
    logContains(SNAPSHOT_INDEX, SNAPSHOT_TERM);

    // when
    final var response = install(firstChunk());

    // then
    assertSnapshotNotNeeded(response);
    verify(snapshotStore, never()).newReceivedSnapshot(any());
    verify(raft, never()).notifySnapshotReplicationStarted();
  }

  @Test
  void shouldSkipEveryChunkWhenLogHasTheSnapshotsLastEntry() {
    // given - a leader which does not know the flag keeps sending chunks
    logContains(SNAPSHOT_INDEX, SNAPSHOT_TERM);
    install(firstChunk());

    // when
    final var response = install(lastChunk());

    // then
    assertSnapshotNotNeeded(response);
    verify(snapshotStore, never()).newReceivedSnapshot(any());
    verify(log, never()).reset(anyLong());
  }

  @Test
  void shouldInstallSnapshotWhenLogHasDifferentTermAtSnapshotIndex() {
    // given
    logContains(SNAPSHOT_INDEX, SNAPSHOT_TERM + 1);

    // when
    final var response = install(onlyChunk());

    // then
    assertThat(response.status()).isEqualTo(Status.OK);
    assertThat(response.snapshotNotNeeded()).isFalse();
    verify(receivedSnapshot).persist();
    verify(log).reset(SNAPSHOT_INDEX + 1);
    // the reset removes every entry, so a configuration of a removed entry must not survive
    verify(cluster).rollbackConfigurationAfterTruncation(SNAPSHOT_INDEX);
  }

  @Test
  void shouldSkipSnapshotWhenCommittedPastIt() {
    // given
    when(raft.getCommitIndex()).thenReturn(SNAPSHOT_INDEX + 1);

    // when
    final var response = install(firstChunk());

    // then
    assertSnapshotNotNeeded(response);
    verify(snapshotStore, never()).newReceivedSnapshot(any());
  }

  @Test
  void shouldSkipSnapshotWhenItHasTheSnapshot() {
    // given
    when(raft.getCurrentSnapshotIndex()).thenReturn(SNAPSHOT_INDEX);

    // when
    final var response = install(firstChunk());

    // then
    assertSnapshotNotNeeded(response);
    verify(snapshotStore, never()).newReceivedSnapshot(any());
  }

  @Test
  void shouldSkipSnapshotWhenTheSnapshotStoreHasIt() {
    // given
    when(snapshotStore.newReceivedSnapshot(SNAPSHOT_ID))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(
                new SnapshotAlreadyExistsException("exists")));

    // when
    final var response = install(firstChunk());

    // then
    assertSnapshotNotNeeded(response);
  }

  @Test
  void shouldAbortPendingInstallWhenLogReceivedTheSnapshotsLastEntry() {
    // given - the install started, and then the log received the snapshot's last entry
    install(firstChunk());
    verify(raft).notifySnapshotReplicationStarted();
    logContains(SNAPSHOT_INDEX, SNAPSHOT_TERM);

    // when
    final var response = install(lastChunk());

    // then
    assertSnapshotNotNeeded(response);
    verify(receivedSnapshot).abort();
    verify(raft).notifySnapshotReplicationCompleted();
    verify(receivedSnapshot, never()).persist();
    verify(log, never()).reset(anyLong());
  }

  @Test
  void shouldNotStopInstallOnRetriedChunk() {
    // given
    install(firstChunk());

    // when
    final var response = install(firstChunk());

    // then
    assertThat(response.status()).isEqualTo(Status.OK);
    assertThat(response.snapshotNotNeeded()).isFalse();
    verify(receivedSnapshot, never()).abort();
  }

  private InstallResponse install(final InstallRequest request) {
    return role.onInstall(request).join();
  }

  private void logContains(final long index, final long term) {
    final var entry = mock(IndexedRaftLogEntry.class);
    when(entry.index()).thenReturn(index);
    when(entry.term()).thenReturn(term);
    final var reader = mock(RaftLogReader.class);
    when(reader.seek(index)).thenReturn(index);
    when(reader.hasNext()).thenReturn(true);
    when(reader.next()).thenReturn(entry);
    when(log.isEmpty()).thenReturn(false);
    when(log.getFirstIndex()).thenReturn(1L);
    when(log.getLastIndex()).thenReturn(index + 1);
    when(log.openUncommittedReader()).thenReturn(reader);
  }

  private static void assertSnapshotNotNeeded(final InstallResponse response) {
    assertThat(response.status()).isEqualTo(Status.OK);
    assertThat(response.snapshotNotNeeded()).isTrue();
  }

  private static InstallRequest firstChunk() {
    return chunk("chunk-1", true, false, "chunk-2");
  }

  private static InstallRequest lastChunk() {
    return chunk("chunk-2", false, true, null);
  }

  private static InstallRequest onlyChunk() {
    return chunk("chunk-1", true, true, null);
  }

  private static InstallRequest chunk(
      final String name, final boolean initial, final boolean complete, final String nextName) {
    final var chunk = mock(SnapshotChunk.class);
    when(chunk.getSnapshotId()).thenReturn(SNAPSHOT_ID);
    when(chunk.getTotalCount()).thenReturn(2);
    when(chunk.getChunkName()).thenReturn(name);
    when(chunk.getContentBuffer()).thenReturn(ByteBuffer.wrap(new byte[1]));

    return InstallRequest.builder()
        .withCurrentTerm(TERM)
        .withLeader(MemberId.from("0"))
        .withIndex(SNAPSHOT_INDEX)
        .withTerm(SNAPSHOT_TERM)
        .withVersion(1)
        .withData(new SnapshotChunkImpl(chunk).toByteBuffer())
        .withChunkId(id(name))
        .withInitial(initial)
        .withComplete(complete)
        .withNextChunkId(nextName == null ? null : id(nextName))
        .build();
  }

  private static ByteBuffer id(final String name) {
    return ByteBuffer.wrap(name.getBytes(StandardCharsets.UTF_8));
  }
}
