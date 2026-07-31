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
    final VersionedAppendRequest request = appendRequest(2, 0, 0, 1, entries);

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
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 2, entries);

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
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 2, entries);

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
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 2, entries);

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
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 3, entries);

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
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 1, entries);
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
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 1, entries);
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

  @Test
  public void shouldFailAppendWhenLogIsTruncatedBeforeFlushCompletes() {
    // given - entries were appended, but the flush covering them is still in progress
    final var flushFuture = new CompletableFuture<Void>();
    when(log.flush(anyLong())).thenReturn(flushFuture);
    when(log.getTruncationGeneration()).thenReturn(7L);
    runThreadContextInline();

    final var entries = List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1]));
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 1, entries);
    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class));
    final var responseFuture = role.handleAppend(ProtocolVersionHandler.transform(request));

    // when - the log is truncated before the flush completes, e.g. by a conflicting append
    when(log.getTruncationGeneration()).thenReturn(8L);
    flushFuture.complete(null);

    // then - the append is rejected instead of acknowledging entries which may no longer exist
    assertThat(responseFuture).isCompleted();
    assertThat(responseFuture.join().succeeded()).isFalse();
    verify(ctx, never()).setCommitIndex(anyLong());
  }

  @Test
  public void shouldAnnounceCommitIndexBeforeItIsDurable() {
    // given - a flush which does not complete immediately
    final var flushFuture = new CompletableFuture<Void>();
    when(log.flush(anyLong())).thenReturn(flushFuture);
    runThreadContextInline();

    final var entries = List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1]));
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 1, entries);
    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class));

    // when
    final var responseFuture = role.handleAppend(ProtocolVersionHandler.transform(request));

    // then - the log knows about the announced commit index right away, so that it keeps refusing
    // to delete the committed entry, while the commit index itself is only advanced once durable
    verify(log).announceCommitIndex(1);
    verify(ctx, never()).setCommitIndex(anyLong());
    assertThat(responseFuture).isNotCompleted();
  }

  @Test
  public void shouldNotAckEmptyAppendWhileEarlierRecordsAreNotDurable() {
    // given - records up to index 2 are appended but only flushed up to index 1, e.g. because the
    // flush of a previous append request is still in progress
    final var flushFuture = new CompletableFuture<Void>();
    when(log.flush(anyLong())).thenReturn(flushFuture);
    when(log.getLastFlushedIndex()).thenReturn(1L);
    givenLastEntry(2, 1);
    runThreadContextInline();

    // an empty append, e.g. a heartbeat, acknowledging everything up to index 2
    final VersionedAppendRequest request = appendRequest(1, 1, 2, 2, List.of());

    // when
    final var responseFuture = role.handleAppend(ProtocolVersionHandler.transform(request));

    // then - the acknowledgement waits for durability of the acknowledged records
    verify(log).flush(2L);
    assertThat(responseFuture).isNotCompleted();

    flushFuture.complete(null);
    assertThat(responseFuture).isCompleted();
    assertThat(responseFuture.join().succeeded()).isTrue();
    assertThat(responseFuture.join().lastLogIndex()).isEqualTo(2);
  }

  @Test
  public void shouldAckEmptyAppendAtLogStartWithoutFlushing() {
    // given - an empty log where nothing was ever flushed, e.g. a freshly bootstrapped follower
    when(log.getLastFlushedIndex()).thenReturn(-1L);

    // an empty append at the very start of the log, acknowledging no records at all
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 0, List.of());

    // when
    final var responseFuture = role.handleAppend(ProtocolVersionHandler.transform(request));

    // then - acknowledged immediately, nothing needs to be durable
    verify(log, never()).flush(anyLong());
    assertThat(responseFuture).isCompleted();
    assertThat(responseFuture.join().succeeded()).isTrue();
  }

  @Test
  public void shouldAckEmptyAppendImmediatelyWhenRecordsAreDurable() {
    // given - everything appended is already flushed
    when(log.getLastFlushedIndex()).thenReturn(2L);
    givenLastEntry(2, 1);

    final VersionedAppendRequest request = appendRequest(1, 1, 2, 2, List.of());

    // when
    final var responseFuture = role.handleAppend(ProtocolVersionHandler.transform(request));

    // then - the acknowledgement is immediate, without any flush
    verify(log, never()).flush(anyLong());
    assertThat(responseFuture).isCompleted();
    assertThat(responseFuture.join().succeeded()).isTrue();
  }

  @Test
  public void shouldNotAbortPendingSnapshotOnEmptyAppend() throws Exception {
    // given - a pending snapshot is in progress
    final ReceivedSnapshot receivedSnapshot = mock(ReceivedSnapshot.class);
    setPendingSnapshot(receivedSnapshot);

    // an empty append request (heartbeat)
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 0, List.of());

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
    final VersionedAppendRequest request = appendRequest(1, 0, 0, 1, entries);

    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class));

    // when
    role.handleAppend(ProtocolVersionHandler.transform(request)).join();

    // then - the pending snapshot should be aborted
    verify(receivedSnapshot).abort();
    assertThat(getPendingSnapshot()).as("pending snapshot should be cleared").isNull();
  }

  private static VersionedAppendRequest appendRequest(
      final long term,
      final long prevLogTerm,
      final long prevLogIndex,
      final long commitIndex,
      final List<ReplicatableJournalRecord> entries) {
    return VersionedAppendRequest.builder()
        .withTerm(term)
        .withLeader(MemberId.anonymous())
        .withPrevLogTerm(prevLogTerm)
        .withPrevLogIndex(prevLogIndex)
        .withEntries(entries)
        .withCommitIndex(commitIndex)
        .build();
  }

  private void givenLastEntry(final long index, final long term) {
    final var lastEntry = mock(IndexedRaftLogEntry.class);
    when(lastEntry.index()).thenReturn(index);
    when(lastEntry.term()).thenReturn(term);
    when(log.getLastEntry()).thenReturn(lastEntry);
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
}
