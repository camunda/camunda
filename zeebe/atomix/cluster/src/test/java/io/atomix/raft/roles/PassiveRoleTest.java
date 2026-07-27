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
import static org.mockito.Mockito.doAnswer;
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
import io.atomix.raft.storage.system.Configuration;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.journal.CheckedJournalException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.AutoClose;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;

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
    when(log.flush(anyLong())).thenReturn(CompletableFuture.completedFuture(null));
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
  public void shouldFlushAfterAppendRequest() throws CheckedJournalException {
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
    verify(log, times(1)).flush(2L);
    assertThat(response.lastLogIndex()).isEqualTo(2);
  }

  @Test
  public void shouldFlushAfterPartiallyAppendedRequest() throws CheckedJournalException {
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
    verify(log, times(1)).flush(1L);
    assertThat(response.lastLogIndex()).isOne();
  }

  @Test
  public void shouldNotFlushIfNoEntryIsAppended() throws CheckedJournalException {
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
    verify(log, never()).flush(anyLong());
    assertThat(response.lastLogIndex()).isZero();
  }

  @Test
  public void shouldFlushEventWithFailure() throws CheckedJournalException {
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
    verify(log, times(1)).flush(2L);
  }

  @Test
  public void shouldAppendOldVersion() throws CheckedJournalException {
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
  public void shouldCompleteFutureWithErrorIfAppendFails() throws CheckedJournalException {
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
  public void shouldAckOnlyAfterFlushCompleted() {
    // given - a flush which does not complete immediately
    final var flushFuture = new CompletableFuture<Void>();
    when(log.flush(anyLong())).thenReturn(flushFuture);
    runThreadContextInline();

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
    final var responseFuture = role.handleAppend(ProtocolVersionHandler.transform(request));

    // then - the request is only acknowledged, and the commit index only advanced, once the
    // flush covering the appended entries completed
    assertThat(responseFuture).isNotCompleted();
    verify(ctx, never()).setCommitIndex(anyLong());

    flushFuture.complete(null);
    assertThat(responseFuture).isCompleted();
    assertThat(responseFuture.join().succeeded()).isTrue();
    verify(ctx).setCommitIndex(1);
  }

  @Test
  public void shouldFailAppendWhenAsyncFlushFails() {
    // given - a flush which fails asynchronously
    final var flushFuture = new CompletableFuture<Void>();
    when(log.flush(anyLong())).thenReturn(flushFuture);
    runThreadContextInline();

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
    final var responseFuture = role.handleAppend(ProtocolVersionHandler.transform(request));
    flushFuture.completeExceptionally(
        new CheckedJournalException.FlushException(new IOException("failed to sync")));

    // then - the append is rejected so that the leader retries, and the commit index is untouched
    assertThat(responseFuture).isCompleted();
    assertThat(responseFuture.join().succeeded()).isFalse();
    verify(ctx, never()).setCommitIndex(anyLong());
  }

  private void runThreadContextInline() {
    final var threadContext = mock(ThreadContext.class);
    doAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(threadContext)
        .execute(any(Runnable.class));
    when(ctx.getThreadContext()).thenReturn(threadContext);
  }

  @Test
  public void shouldNotAbortPendingSnapshotOnEmptyAppend() throws Exception {
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
  public void shouldAbortPendingSnapshotOnNonEmptyAppend() throws Exception {
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
  public void shouldCheckForDataLossWithTheIndexTheLeaderAgreesOn() throws CheckedJournalException {
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
  public void shouldCheckForDataLossWithTheLocalLogEndWhenPassive() throws CheckedJournalException {
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
  public void shouldStoreTheConfigurationsOwnTerm() {
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
