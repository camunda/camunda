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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.impl.RaftClusterContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RaftReplicationMetrics;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.ProtocolVersionHandler;
import io.atomix.raft.protocol.ReplicatableJournalRecord;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.system.Configuration;
import io.camunda.zeebe.journal.CheckedJournalException;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalException.InvalidChecksum;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
final class PassiveRoleTest {

  private RaftLog log;
  private PassiveRole role;
  private RaftContext ctx;
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void setup() throws IOException {
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
    when(ctx.getCluster()).thenReturn(mock(RaftClusterContext.class));

    role = new PassiveRole(ctx);
  }

  @Test
  void shouldFailAppendWithIncorrectChecksum() {
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
  void shouldFlushAfterAppendRequest() throws CheckedJournalException {
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
  void shouldFlushAfterPartiallyAppendedRequest() throws CheckedJournalException {
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
  void shouldNotFlushIfNoEntryIsAppended() throws CheckedJournalException {
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
  void shouldNotAcknowledgeEntriesWhenFlushFails() throws CheckedJournalException {
    // given
    doThrow(new FlushException(new IOException("Input/output error"))).when(log).flush();
    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class));
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1])))
            .withCommitIndex(1)
            .build();

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - neither acknowledged nor committed
    assertThat(response.succeeded()).isFalse();
    assertThat(response.lastLogIndex()).isZero();
    verify(ctx, never()).setCommitIndex(anyLong());
  }

  @Test
  void shouldFlushEventWithFailure() throws CheckedJournalException {
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
  void shouldAppendOldVersion() throws CheckedJournalException {
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

  @Test
  void shouldCompleteFutureWithErrorIfAppendFails() throws CheckedJournalException {
    // given
    final var entries = List.of(new PersistedRaftRecord(1, 1, 1, 1, new byte[1]));
    final var request = new AppendRequest(2, "a", 0, 0, entries, 1);
    when(log.append(any(PersistedRaftRecord.class))).thenThrow(new IllegalStateException("error"));

    // when
    final var result =
        role.handleAppend(ProtocolVersionHandler.transform(request)).toCompletableFuture().join();
    // then
    assertThat(result.succeeded()).isFalse();
  }

  @Test
  void shouldNotAbortPendingSnapshotOnEmptyAppend() throws Exception {
    // given - a pending snapshot is in progress
    final ReceivedSnapshot receivedSnapshot = mock(ReceivedSnapshot.class);
    setPendingSnapshot(receivedSnapshot);

    // an empty append request (heartbeat)
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of())
            .withCommitIndex(0)
            .build();

    // when
    role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the pending snapshot should not be aborted
    verify(receivedSnapshot, never()).abort();
    assertThat(getPendingSnapshot()).as("pending snapshot should still be present").isNotNull();
  }

  @Test
  void shouldAbortPendingSnapshotOnNonEmptyAppend() throws Exception {
    // given - a pending snapshot is in progress
    final ReceivedSnapshot receivedSnapshot = mock(ReceivedSnapshot.class);
    setPendingSnapshot(receivedSnapshot);

    // an append request with entries
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

    // when
    role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the pending snapshot should be aborted
    verify(receivedSnapshot).abort();
    assertThat(getPendingSnapshot()).as("pending snapshot should be cleared").isNull();
  }

  @Test
  void shouldRollBackConfigurationWhenAppendRequestResetsTheLog() throws CheckedJournalException {
    // given - a request that starts the leader's log, as its previous term is zero, while the local
    // log starts later, e.g. because it was compacted
    when(log.getFirstIndex()).thenReturn(5L);
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1])))
            .withCommitIndex(1)
            .build();
    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class));

    // when
    role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - a configuration that only the erased log knew must not survive
    verify(log).reset(1);
    verify(ctx.getCluster()).reloadStoredConfiguration();
  }

  @Test
  void shouldKeepMatchingEntriesWhenAppendRequestStartsTheLog() throws CheckedJournalException {
    // given - the local log starts at the leader's first entry and already holds it, e.g. because
    // this member kept the entry after it rejected an append whose flush failed
    final var lastEntry = mock(IndexedRaftLogEntry.class);
    when(lastEntry.index()).thenReturn(1L);
    when(lastEntry.term()).thenReturn(1L);
    when(log.getFirstIndex()).thenReturn(1L);
    when(log.getLastIndex()).thenReturn(1L);
    when(log.getLastEntry()).thenReturn(lastEntry);
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1])))
            .withCommitIndex(0)
            .build();

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the entry, which the leader may have committed with this member, is kept
    verify(log, never()).reset(anyLong());
    verify(log, never()).deleteAfter(anyLong());
    assertThat(response.succeeded()).isTrue();
    assertThat(response.lastLogIndex()).isOne();
  }

  @Test
  void shouldAcceptAppendWhosePreviousEntryIsTheSnapshotWhileTheLogHasEntriesAfterIt()
      throws CheckedJournalException {
    // given - the log starts after the snapshot at index 9, e.g. because this member kept the
    // entries 10 to 12 when it rejected an append whose flush failed
    givenLogAfterSnapshot();
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(5)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(4)
            .withPrevLogIndex(9)
            .withEntries(List.of(new ReplicatableJournalRecord(5, 10, 1, new byte[1])))
            .withCommitIndex(10)
            .build();

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the previous entry matches the snapshot, so the entries are kept
    assertThat(response.succeeded()).isTrue();
    assertThat(response.lastLogIndex()).isEqualTo(10);
    verify(log, never()).deleteAfter(anyLong());
    verify(log, never()).reset(anyLong());
  }

  @Test
  void shouldRejectAppendWhosePreviousEntryIsBeforeTheSnapshotWithTheSnapshotIndex() {
    // given
    givenLogAfterSnapshot();
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(5)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(4)
            .withPrevLogIndex(8)
            .withEntries(List.of(new ReplicatableJournalRecord(4, 9, 1, new byte[1])))
            .withCommitIndex(10)
            .build();

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the leader continues after the snapshot, instead of going further back
    assertThat(response.succeeded()).isFalse();
    assertThat(response.lastLogIndex()).isEqualTo(9);
    verify(log, never()).append(any(ReplicatableJournalRecord.class));
  }

  @Test
  void shouldRejectAppendWhosePreviousEntryIsTheSnapshotWithAnotherTerm()
      throws CheckedJournalException {
    // given
    givenLogAfterSnapshot();
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(5)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(3)
            .withPrevLogIndex(9)
            .withEntries(List.of(new ReplicatableJournalRecord(5, 10, 1, new byte[1])))
            .withCommitIndex(10)
            .build();

    // when
    final AppendResponse response =
        role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the append is not processed any further
    assertThat(response.succeeded()).isFalse();
    verify(log, never()).append(any(ReplicatableJournalRecord.class));
    verify(log, never()).flush();
  }

  /** A snapshot at index 9 of term 4, and a log with the entries 10 to 12 of term 5. */
  private void givenLogAfterSnapshot() {
    final PersistedSnapshot snapshot = mock(PersistedSnapshot.class);
    when(snapshot.getIndex()).thenReturn(9L);
    when(snapshot.getTerm()).thenReturn(4L);
    when(ctx.getCurrentSnapshot()).thenReturn(snapshot);
    when(ctx.getCurrentSnapshotIndex()).thenReturn(9L);
    when(ctx.getTerm()).thenReturn(5L);

    final var entries = new TreeMap<Long, IndexedRaftLogEntry>();
    for (long index = 10; index <= 12; index++) {
      final var entry = mock(IndexedRaftLogEntry.class);
      when(entry.index()).thenReturn(index);
      when(entry.term()).thenReturn(5L);
      entries.put(index, entry);
    }
    when(log.getFirstIndex()).thenReturn(10L);
    when(log.getLastIndex()).thenReturn(12L);
    when(log.getLastEntry()).thenReturn(entries.lastEntry().getValue());
    // like the journal's reader, seeking before the first entry positions at the first entry
    when(log.openUncommittedReader())
        .thenAnswer(
            ignored -> {
              final var reader = mock(RaftLogReader.class);
              final var position = new AtomicLong();
              when(reader.seek(anyLong()))
                  .thenAnswer(
                      seek -> {
                        position.set(Math.max(seek.<Long>getArgument(0), entries.firstKey()));
                        return position.get();
                      });
              when(reader.hasNext()).thenAnswer(next -> entries.containsKey(position.get()));
              when(reader.next()).thenAnswer(next -> entries.get(position.getAndIncrement()));
              return reader;
            });
  }

  @Test
  void shouldRollBackConfigurationWhenAnEntryOfAnotherTermReplacesTheLastEntry()
      throws CheckedJournalException {
    // given - the last local entry has another term than the entry the leader sends for its index
    when(log.getFirstIndex()).thenReturn(1L);
    final var lastEntry = mock(IndexedRaftLogEntry.class);
    when(lastEntry.index()).thenReturn(1L);
    when(lastEntry.term()).thenReturn(2L);
    when(log.getLastEntry()).thenReturn(lastEntry);
    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class));
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(3)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of(new ReplicatableJournalRecord(3, 1, 1, new byte[1])))
            .withCommitIndex(1)
            .build();

    // when
    role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the divergent entry is deleted and so is a configuration that came from it
    verify(log).deleteAfter(0);
    verify(ctx.getCluster()).dropConfigurationsWithoutLogEntry(0);
  }

  @Test
  void shouldRollBackConfigurationWhenTruncatingTheLogFailsToFlush()
      throws CheckedJournalException {
    // given - the journal is truncated before the flush that fails, so the entry is gone
    when(log.getFirstIndex()).thenReturn(1L);
    final var lastEntry = mock(IndexedRaftLogEntry.class);
    when(lastEntry.index()).thenReturn(1L);
    when(lastEntry.term()).thenReturn(2L);
    when(log.getLastEntry()).thenReturn(lastEntry);
    doThrow(new FlushException(new IOException("flush failed"))).when(log).deleteAfter(0);
    final var request =
        VersionedAppendRequest.builder()
            .withTerm(3)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of(new ReplicatableJournalRecord(3, 1, 1, new byte[1])))
            .withCommitIndex(1)
            .build();

    // when
    role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the retry would not truncate again, so the configuration must be dropped now
    verify(ctx.getCluster()).dropConfigurationsWithoutLogEntry(0);
  }

  @Test
  void shouldCheckForDataLossWithTheIndexTheLeaderAgreesOn() throws CheckedJournalException {
    // given - a promotable member whose log ends far beyond what this append covers
    final var promotableRole = new PromotableRole(ctx);
    when(log.getLastIndex()).thenReturn(120L);
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
    promotableRole.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the data-loss check gets the index up to which this node and the leader agree on
    // persisted data. The local log end must not be used here: it can never lie below this node's
    // own commit index, which would make the majority-data-loss check unsatisfiable.
    verify(ctx).setFirstCommitIndex(2, 2);
  }

  @Test
  void shouldCheckForDataLossWithTheLocalLogEndWhenPassive() throws CheckedJournalException {
    // given - a passive member whose log ends far beyond what this append covers
    when(log.getLastIndex()).thenReturn(120L);
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
    role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the leader replicates to a PASSIVE member from a committed reader, so the request
    // understates the leader's log and cannot be used to detect data loss. The local log end never
    // trips the check, which is the point: an ex-leader demoted to PASSIVE tripped it spuriously.
    verify(ctx).setFirstCommitIndex(2, 120);
  }

  private void setPendingSnapshot(final ReceivedSnapshot snapshot) throws Exception {
    final var field = PassiveRole.class.getDeclaredField("pendingSnapshot");
    field.setAccessible(true);
    field.set(role, snapshot);
  }

  private Object getPendingSnapshot() throws Exception {
    final var field = PassiveRole.class.getDeclaredField("pendingSnapshot");
    field.setAccessible(true);
    return field.get(role);
  }

  /**
   * A configuration is identified by its index and the term of the entry that introduced it, so a
   * member configured by a leader has to store that term - not the leader's current term, which
   * only says who disseminated it. Storing the latter made {@code Configuration#term()} mean one
   * thing on a member that appended the entry and another on a member that was configured, which
   * the leader then rejected as a stale configuration.
   */
  @Test
  void shouldStoreTheConfigurationsOwnTerm() {
    // given
    final var cluster = mock(RaftClusterContext.class);
    when(ctx.getCluster()).thenReturn(cluster);
    // the commit check at the end of onConfigure reads it back; any older configuration will do
    when(cluster.getConfiguration()).thenReturn(new Configuration(1, 1, 1, List.of(), List.of()));

    // when - a leader in term 5 disseminates a configuration whose entry was appended in term 2
    role.onConfigure(
            ConfigureRequest.builder()
                .withTerm(5)
                .withConfigurationTerm(2)
                .withLeader(MemberId.from("1"))
                .withIndex(7)
                .withTime(1)
                .withNewMembers(List.of())
                .build())
        .join();

    // then
    final var configuration = ArgumentCaptor.forClass(Configuration.class);
    verify(cluster).configure(configuration.capture());
    assertThat(configuration.getValue().term())
        .describedAs("the term of the configuration entry, not of its dissemination")
        .isEqualTo(2);
    assertThat(configuration.getValue().index()).isEqualTo(7);
  }
}
