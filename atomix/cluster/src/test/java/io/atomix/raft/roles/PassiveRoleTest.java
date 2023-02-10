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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RaftReplicationMetrics;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalException.InvalidChecksum;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class PassiveRoleTest {

  @Rule public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);
  private RaftLog log;
  private PassiveRole role;
  private RaftContext ctx;

  @Before
  public void setup() throws IOException {
    ctx = mock(RaftContext.class);

    log = mock(RaftLog.class);
    when(log.shouldFlushExplicitly()).thenReturn(true);
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

    role = new PassiveRole(ctx);
  }

  @Test
  public void shouldFailAppendWithIncorrectChecksum() {
    // given
    final List<PersistedRaftRecord> entries =
        List.of(new PersistedRaftRecord(1, 1, 1, 12345, new byte[1]));
    final AppendRequest request = new AppendRequest(2, "", 0, 0, entries, 1);

    when(log.append(any(PersistedRaftRecord.class)))
        .thenThrow(new JournalException.InvalidChecksum("expected"));

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    assertThat(response.succeeded()).isFalse();
  }

  @Test
  public void shouldFlushAfterAppendRequest() {
    // given
    final List<PersistedRaftRecord> entries =
        List.of(
            new PersistedRaftRecord(1, 1, 1, 1, new byte[1]),
            new PersistedRaftRecord(1, 2, 2, 1, new byte[1]));
    final AppendRequest request = new AppendRequest(1, "", 0, 0, entries, 2);

    when(log.append(any(PersistedRaftRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenReturn(mock(IndexedRaftLogEntry.class));

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    verify(log).flush();
    assertThat(response.lastLogIndex()).isEqualTo(2);
  }

  @Test
  public void shouldFlushAfterPartiallyAppendedRequest() {
    // given
    final List<PersistedRaftRecord> entries =
        List.of(
            new PersistedRaftRecord(1, 1, 1, 1, new byte[1]),
            new PersistedRaftRecord(1, 2, 2, 1, new byte[1]));
    final AppendRequest request = new AppendRequest(1, "", 0, 0, entries, 2);

    when(log.append(any(PersistedRaftRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenThrow(new InvalidChecksum.InvalidChecksum("expected"));

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    verify(log).flush();
    assertThat(response.lastLogIndex()).isEqualTo(1);
  }

  @Test
  public void shouldNotFlushIfNoEntryIsAppended() {
    // given
    final List<PersistedRaftRecord> entries =
        List.of(new PersistedRaftRecord(1, 1, 1, 1, new byte[1]));
    final AppendRequest request = new AppendRequest(1, "", 0, 0, entries, 2);

    when(log.append(any(PersistedRaftRecord.class)))
        .thenThrow(new InvalidChecksum.InvalidChecksum("expected"));

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    verify(log, never()).flush();
    assertThat(response.lastLogIndex()).isEqualTo(0);
  }

  @Test
  public void shouldStoreLastFlushedIndex() {
    // given
    final List<PersistedRaftRecord> entries =
        List.of(new PersistedRaftRecord(1, 1, 1, 1, new byte[1]));
    final AppendRequest request = new AppendRequest(2, "", 0, 0, entries, 1);

    when(log.append(any(PersistedRaftRecord.class))).thenReturn(mock(IndexedRaftLogEntry.class));
    when(ctx.getLog()).thenReturn(log);

    // when
    role.handleAppend(request).join();

    // then
    verify(ctx).setLastFlushedIndex(eq(1L));
  }

  @Test
  public void shouldStoreLastWrittenEvenWithFailure() {
    // given
    final List<PersistedRaftRecord> entries =
        List.of(
            new PersistedRaftRecord(1, 1, 1, 1, new byte[1]),
            new PersistedRaftRecord(1, 2, 2, 1, new byte[1]),
            new PersistedRaftRecord(1, 3, 3, 1, new byte[1]));
    final AppendRequest request = new AppendRequest(1, "", 0, 0, entries, 3);

    when(log.append(any(PersistedRaftRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenThrow(new InvalidChecksum("expected"));
    when(ctx.getLog()).thenReturn(log);

    // when
    role.handleAppend(request).join();

    // then
    verify(ctx).setLastFlushedIndex(eq(2L));
  }

  @Test
  public void shouldResetLastFlushedIndexAfterTruncating() {
    // given
    final List<PersistedRaftRecord> entries =
        List.of(
            new PersistedRaftRecord(1, 1, 1, 1, new byte[1]),
            new PersistedRaftRecord(1, 2, 2, 1, new byte[1]),
            new PersistedRaftRecord(1, 3, 3, 1, new byte[1]));
    final AppendRequest request = new AppendRequest(1, "", 0, 0, entries, 0);

    when(log.append(any(PersistedRaftRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenReturn(mock(IndexedRaftLogEntry.class))
        .thenReturn(mock(IndexedRaftLogEntry.class));
    when(ctx.getLog()).thenReturn(log);
    role.handleAppend(request).join();
    verify(ctx).setLastFlushedIndex(eq(3L));

    // when - force truncation
    when(log.getLastIndex()).thenReturn(3L);
    when(ctx.getCommitIndex()).thenReturn(1L);

    role = new PassiveRole(ctx);
    role.start().join();

    // then
    verify(ctx).setLastFlushedIndex(eq(1L));
  }
}
