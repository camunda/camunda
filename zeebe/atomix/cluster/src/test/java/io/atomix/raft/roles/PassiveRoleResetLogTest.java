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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.impl.RaftClusterContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RaftReplicationMetrics;
import io.atomix.raft.protocol.ProtocolVersionHandler;
import io.atomix.raft.protocol.ReplicatableJournalRecord;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The first append of a leader (previous term 0) resets the follower's log. A configuration that
 * came from a deleted entry must be dropped, but a configuration that the leader sent before its
 * first append to an empty log must stay: the leader ignores a reported configuration index of 0
 * and would never send it again.
 */
final class PassiveRoleResetLogTest {

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RaftContext context = mock(RaftContext.class);
  private final RaftLog log = mock(RaftLog.class);
  private final RaftClusterContext cluster = mock(RaftClusterContext.class);
  private PassiveRole role;

  @BeforeEach
  void setup() {
    final ReceivableSnapshotStore store = mock(ReceivableSnapshotStore.class);
    when(store.getLatestSnapshot()).thenReturn(Optional.empty());
    when(log.flushesDirectly()).thenReturn(true);
    when(log.append(any(ReplicatableJournalRecord.class)))
        .thenReturn(mock(IndexedRaftLogEntry.class));
    when(context.getLog()).thenReturn(log);
    when(context.getStorage()).thenReturn(mock(RaftStorage.class));
    when(context.getPersistedSnapshotStore()).thenReturn(store);
    when(context.getTerm()).thenReturn(1L);
    when(context.getReplicationMetrics()).thenReturn(mock(RaftReplicationMetrics.class));
    when(context.getMeterRegistry()).thenReturn(meterRegistry);
    when(context.getName()).thenReturn("partition-1");
    when(context.getCluster()).thenReturn(cluster);
    role = new PassiveRole(context);
  }

  private void appendFirstEntry() {
    final VersionedAppendRequest request =
        VersionedAppendRequest.builder()
            .withTerm(1)
            .withLeader(MemberId.anonymous())
            .withPrevLogTerm(0)
            .withPrevLogIndex(0)
            .withEntries(List.of(new ReplicatableJournalRecord(1, 1, 1, new byte[1])))
            .withCommitIndex(1)
            .build();
    role.handleAppend(ProtocolVersionHandler.transform(request))
        .orTimeout(30, TimeUnit.SECONDS)
        .join();
  }

  @Test
  void shouldKeepConfigurationReceivedBeforeFirstAppendOnEmptyLog() {
    // given
    when(log.isEmpty()).thenReturn(true);

    // when
    appendFirstEntry();

    // then
    verify(log).reset(1);
    verify(cluster, never()).reloadStoredConfiguration();
  }

  @Test
  void shouldDropConfigurationOfDeletedEntriesOnReset() {
    // given
    when(log.isEmpty()).thenReturn(false);

    // when
    appendFirstEntry();

    // then
    verify(log).reset(1);
    verify(cluster).reloadStoredConfiguration();
  }
}
