/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RaftReplicationMetrics;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.ProtocolVersionHandler;
import io.atomix.raft.protocol.ReplicatableJournalRecord;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalException.InvalidChecksum;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.AutoClose;
import org.junit.rules.Timeout;

public class PassiveRoleTest {

  @Rule public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);
  private RaftLog log;
  private PassiveRole role;
  private RaftContext ctx;
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Before
  public void setup() throws IOException {
    ctx = mock(RaftContext.class);

    log = mock(RaftLog.class);
    when(log.flushesDirectly()).thenReturn(true);
    when(ctx.getLog()).thenReturn(log);

    final PersistedSnapshot snapshot = mock(PersistedSnapshot.class);
    when(snapshot.getIndex()).thenReturn(1L);
    when(snapshot.getTerm()).thenReturn(1L);

    final ReceivableSnapshotStore store = mock(ReceivableSnapshotStore.class);
    when(store.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

    final RaftStorage storage = mock(RaftStorage.class);
    when(ctx.getStorage()).thenReturn(storage);
    when(ctx.getLog()).thenReturn(log);
    when(ctx.getPersistedSnapshotStore()).thenReturn(store);
    when(ctx.getTerm()).thenReturn(1L);
    when(ctx.getReplicationMetrics()).thenReturn(mock(RaftReplicationMetrics.class));
    when(ctx.getMeterRegistry()).thenReturn(meterRegistry);
    when(ctx.getName()).thenReturn("partition-1");

    role = new PassiveRole(ctx);
  }

  @Test
  public void shouldFailAppendWithIncorrectChecksum() {
    // given
    final var entries = List.of(new ReplicatableJournalRecord(1, 1, 12345, new byte[1]));
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(2)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(entries)
            .withCommitIndex(1)
            .build();

    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenThrow(new JournalException.InvalidChecksum("expected"));

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then
    assertThat(response.succeeded()).isFalse();
  }

  @Test
  public void shouldFlushAfterAppendRequest() {
    // given
    final var entries =
        List.of(
            new ReplicatableJournalRecord(1, 1, 1, new byte[1]),
            new ReplicatableJournalRecord(1, 2, 1, new byte[1]));
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(entries)
            .withCommitIndex(2)
            .build();

    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenReturn(mock(IndexedRaftLogEntry.class));

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then
    verify(log, times(1)).flush();
    assertThat(response.lastLogIndex()).isEqualTo(2);
  }

  @Test
  public void shouldFlushAfterPartiallyAppendedRequest() {
    // given
    final var entries =
        List.of(
            new ReplicatableJournalRecord(1, 1, 1, new byte[1]),
            new ReplicatableJournalRecord(1, 2, 1, new byte[1]));
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(entries)
            .withCommitIndex(2)
            .build();

    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenThrow(new InvalidChecksum.InvalidChecksum("expected"));

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then
    verify(log, times(1)).flush();
    assertThat(response.lastLogIndex()).isOne();
  }

  @Test
  public void shouldNotFlushIfNoEntryIsAppended() {
    // given
    final var entries = List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1]));
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(entries)
            .withCommitIndex(2)
            .build();

    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenThrow(new InvalidChecksum.InvalidChecksum("expected"));

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then
    verify(log, never()).flush();
    assertThat(response.lastLogIndex()).isZero();
  }

  @Test
  public void shouldFlushEventWithFailure() {
    // given
    final var entries =
        List.of(
            new ReplicatableJournalRecord(1, 1, 1, new byte[1]),
            new ReplicatableJournalRecord(1, 2, 1, new byte[1]),
            new ReplicatableJournalRecord(1, 3, 1, new byte[1]));
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(entries)
            .withCommitIndex(3)
            .build();

    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenThrow(new InvalidChecksum("expected"));
    when(ctx.getLog()).thenReturn(log);

    // when
    role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then
    verify(log, times(1)).flush();
  }

  @Test
  public void shouldAppendOldVersion() {
    // given
    final var entries = List.of(new PersistedRaftRecord(1, 1, 1, 1, new byte[1]));
    final var request = new AppendRequest(2, "a", 0, 0, entries, 1);

    when(log.append(any(PersistedRaftRecord.class))).thenReturn(mock(IndexedRaftLogEntry.class));

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then
    assertThat(response.succeeded()).isTrue();
  }

  // --- Tests for SUPPORT-31571: conditional snapshot abort in handleAppend ---

  @Test
  public void shouldNotAbortPendingSnapshotOnHeartbeat() throws Exception {
    // given - a pending snapshot is in progress
    final ReceivedSnapshot pendingSnapshot = mock(ReceivedSnapshot.class);
    setPendingSnapshot(role, pendingSnapshot);

    // An empty heartbeat AppendRequest (no entries) from the leader
    final VersionedAppendRequest heartbeat =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of())
            .withCommitIndex(0)
            .build();

    // The log doesn't advance (no entries to append), lastIndex stays at 0
    when(log.getLastIndex()).thenReturn(0L);

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(heartbeat)).join();

    // then - the pending snapshot must NOT be aborted
    verify(pendingSnapshot, never()).abort();
    assertThat(response.succeeded()).isTrue();
    // Verify the snapshot is still set (not nulled out)
    assertThat(getPendingSnapshot(role)).isSameAs(pendingSnapshot);
  }

  @Test
  public void shouldAbortPendingSnapshotWhenEntriesAppended() throws Exception {
    // given - a pending snapshot is in progress
    final ReceivedSnapshot pendingSnapshot = mock(ReceivedSnapshot.class);
    setPendingSnapshot(role, pendingSnapshot);

    // An AppendRequest with real entries
    final var entries = List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1]));
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(entries)
            .withCommitIndex(1)
            .build();

    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class));
    // Log advances: before append returns 0, after append returns 1
    when(log.getLastIndex()).thenReturn(0L).thenReturn(1L);

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the pending snapshot MUST be aborted because the log advanced
    verify(pendingSnapshot, times(1)).abort();
    assertThat(response.succeeded()).isTrue();
    // Verify the snapshot was nulled out
    assertThat(getPendingSnapshot(role)).isNull();
  }

  @Test
  public void shouldNotAbortWhenNoPendingSnapshot() {
    // given - no pending snapshot (default state)
    // An empty heartbeat
    final VersionedAppendRequest heartbeat =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of())
            .withCommitIndex(0)
            .build();

    when(log.getLastIndex()).thenReturn(0L);

    // when - should not throw or cause any issues
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(heartbeat)).join();

    // then
    assertThat(response.succeeded()).isTrue();
  }

  @Test
  public void shouldNotAbortPendingSnapshotWhenAppendFails() throws Exception {
    // given - a pending snapshot is in progress
    final ReceivedSnapshot pendingSnapshot = mock(ReceivedSnapshot.class);
    setPendingSnapshot(role, pendingSnapshot);

    // An AppendRequest with entries, but prevLogIndex is ahead of local log (will fail
    // checkPreviousEntry)
    final var entries = List.of(new ReplicatableJournalRecord(1, 100, 1, new byte[1]));
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(1)
            .withPrevLogIndex(99)
            .withEntries(entries)
            .withCommitIndex(100)
            .build();

    // Local log only has index 0 — prevLogIndex=99 is way ahead, so checkPreviousEntry fails
    final IndexedRaftLogEntry lastEntry = mock(IndexedRaftLogEntry.class);
    when(lastEntry.index()).thenReturn(0L);
    when(lastEntry.term()).thenReturn(0L);
    when(log.getLastEntry()).thenReturn(lastEntry);
    when(log.getLastIndex()).thenReturn(0L);

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the append failed, so the pending snapshot must NOT be aborted
    verify(pendingSnapshot, never()).abort();
    assertThat(response.succeeded()).isFalse();
    // Verify the snapshot is still set
    assertThat(getPendingSnapshot(role)).isSameAs(pendingSnapshot);
  }

  @Test
  public void shouldNotAbortPendingSnapshotWhenEntryFailsToAppend() throws Exception {
    // given - a pending snapshot is in progress
    final ReceivedSnapshot pendingSnapshot = mock(ReceivedSnapshot.class);
    setPendingSnapshot(role, pendingSnapshot);

    // An AppendRequest with entries that will fail on append (e.g., invalid checksum)
    final var entries = List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1]));
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(entries)
            .withCommitIndex(1)
            .build();

    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenThrow(new InvalidChecksum("expected"));
    // Log does NOT advance because the append fails
    when(log.getLastIndex()).thenReturn(0L);

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the entry failed to append so the log didn't advance; snapshot preserved
    verify(pendingSnapshot, never()).abort();
    assertThat(response.succeeded()).isFalse();
    assertThat(getPendingSnapshot(role)).isSameAs(pendingSnapshot);
  }

  /**
   * Sets the private pendingSnapshot field via reflection. This is necessary because the field is
   * private and there's no public setter — snapshots are normally set via the onInstall() path.
   */
  private static void setPendingSnapshot(final PassiveRole role, final ReceivedSnapshot snapshot)
      throws Exception {
    final Field field = PassiveRole.class.getDeclaredField("pendingSnapshot");
    field.setAccessible(true);
    field.set(role, snapshot);
  }

  /** Reads the private pendingSnapshot field via reflection for assertion purposes. */
  private static ReceivedSnapshot getPendingSnapshot(final PassiveRole role) throws Exception {
    final Field field = PassiveRole.class.getDeclaredField("pendingSnapshot");
    field.setAccessible(true);
    return (ReceivedSnapshot) field.get(role);
  }
}
