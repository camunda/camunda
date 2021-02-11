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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RaftReplicationMetrics;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.StorageException.InvalidChecksum;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespace.Builder;
import io.atomix.utils.serializer.Namespaces;
import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.ReceivableSnapshotStore;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class PassiveRoleTest {

  private static final Namespace NAMESPACE =
      new Builder().register(Namespaces.BASIC).register(ZeebeEntry.class).build();
  @Rule public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);
  private final ZeebeEntry entry = new ZeebeEntry(1, 1, 0, 1, ByteBuffer.allocate(0));
  private RaftLog log;
  private PassiveRole role;

  @Before
  public void setup() {
    final RaftStorage storage = mock(RaftStorage.class);
    when(storage.namespace()).thenReturn(NAMESPACE);

    log = mock(RaftLog.class);
    when(log.getLastIndex()).thenReturn(1L);

    final PersistedSnapshot snapshot = mock(PersistedSnapshot.class);
    when(snapshot.getIndex()).thenReturn(1L);
    when(snapshot.getTerm()).thenReturn(1L);

    final ReceivableSnapshotStore store = mock(ReceivableSnapshotStore.class);
    when(store.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

    final RaftContext ctx = mock(RaftContext.class);
    when(ctx.getStorage()).thenReturn(storage);
    when(ctx.getLog()).thenReturn(log);
    when(ctx.getPersistedSnapshotStore()).thenReturn(store);
    when(ctx.getTerm()).thenReturn(1L);
    when(ctx.getReplicationMetrics()).thenReturn(mock(RaftReplicationMetrics.class));
    when(ctx.getLog()).thenReturn(mock(RaftLog.class));

    role = new PassiveRole(ctx);
  }

  @Test
  public void shouldRejectRequestIfDifferentNumberEntriesAndChecksums() {
    // given
    final List<RaftLogEntry> entries = generateEntries(1);
    final AppendRequest request = new AppendRequest(2, "", 1, 1, entries, List.of(1L, 2L), 1);

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    assertThat(response.succeeded()).isFalse();
  }

  @Test
  public void shouldAppendEntryWithCorrectChecksum() {
    // given
    when(log.append(any(), anyLong())).thenReturn(new Indexed<>(1, entry, 1, 1));
    final List<RaftLogEntry> entries = generateEntries(1);
    final List<Long> checksums = getChecksums(entries);
    final AppendRequest request = new AppendRequest(2, "", 1, 1, entries, checksums, 1);

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    assertThat(response.succeeded()).isTrue();
    verify(log).append(any(ZeebeEntry.class), eq(checksums.get(0)));
    verify(log, never()).append(any(ZeebeEntry.class));
  }

  @Test
  public void shouldNotValidateIfNoChecksum() {
    // given
    when(log.append(any(ZeebeEntry.class))).thenReturn(new Indexed<>(1, entry, 1, 1));
    final List<RaftLogEntry> entries = generateEntries(1);
    final AppendRequest request = new AppendRequest(2, "", 1, 1, entries, null, 1);

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    assertThat(response.succeeded()).isTrue();
    verify(log).append(any(ZeebeEntry.class));
    verify(log, never()).append(any(ZeebeEntry.class), anyLong());
  }

  @Test
  public void shouldFailAppendWithIncorrectChecksum() {
    // given
    when(log.append(any(ZeebeEntry.class), anyLong()))
        .thenReturn(new Indexed<>(1, entry, 1, 1))
        .thenThrow(new InvalidChecksum("expected"));
    final List<RaftLogEntry> entries = generateEntries(2);
    final List<Long> checksums = getChecksums(entries);
    checksums.set(1, 0L);
    final AppendRequest request = new AppendRequest(2, "", 1, 1, entries, checksums, 1);

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    assertThat(response.succeeded()).isFalse();
    assertThat(response.lastLogIndex()).isEqualTo(2);
    verify(log, times(2)).append(any(ZeebeEntry.class), anyLong());
  }

  private List<RaftLogEntry> generateEntries(final int numEntries) {
    final List<RaftLogEntry> entries = new ArrayList<>();
    for (int i = 0; i < numEntries; i++) {
      entries.add(new ZeebeEntry(1, 1, i + 1, i + 1, ByteBuffer.allocate(0)));
    }

    return entries;
  }

  private List<Long> getChecksums(final List<RaftLogEntry> entries) {
    final List<Long> checksums = new ArrayList<>();

    for (final RaftLogEntry entry : entries) {
      final byte[] serialized = NAMESPACE.serialize(entry);
      final Checksum crc32 = new CRC32();
      crc32.update(serialized, 0, serialized.length);

      checksums.add(crc32.getValue());
    }

    return checksums;
  }
}
