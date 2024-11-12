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
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.impl.RaftContext.State;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class RaftFailOverIT {
  private static final Logger LOG = LoggerFactory.getLogger(RaftFailOverIT.class);
  @Rule @Parameter public RaftRule raftRule;

  @Parameters(name = "{index}: {0}")
  public static Object[][] raftConfigurations() {
    return new Object[][] {
      new Object[] {RaftRule.withBootstrappedNodes(3)},
      new Object[] {RaftRule.withBootstrappedNodes(4)},
      new Object[] {RaftRule.withBootstrappedNodes(5)}
    };
  }

  @Test
  public void shouldCommitEntriesAfterFollowerShutdown() throws Throwable {
    // given
    final var entryCount = 20;
    raftRule.appendEntries(entryCount);
    raftRule.shutdownFollower();

    // when
    final var lastIndex = raftRule.appendEntries(entryCount);

    // then
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
    final var memberLog = raftRule.getMemberLogs();

    final var maxIndex =
        memberLog.values().stream()
            .flatMap(Collection::stream)
            .map(IndexedRaftLogEntry::index)
            .max(Long::compareTo)
            .orElseThrow();
    assertThat(maxIndex).isEqualTo(lastIndex);
    assertMemberLogs(memberLog);
  }

  @Test
  public void onFollowerRestartItBecomesReady() throws Exception {
    Awaitility.await("Leader is up").until(() -> raftRule.getLeader().isPresent());
    assertThat(
            raftRule.getServers().stream()
                .map(s -> s.getContext().getCommitIndex())
                .distinct()
                .count())
        .isEqualTo(1L);

    final var follower = raftRule.shutdownFollower();
    raftRule.joinCluster(follower);

    final var server = raftRule.getServer(follower);
    Awaitility.await("Follower should be ready")
        .until(() -> server.getContext().getState() == State.READY);
  }

  @Test
  public void onFollowerRestartItBecomesReadyOnlyAfterCatchingUp() throws Exception {
    Awaitility.await("Leader is up").until(() -> raftRule.getLeader().isPresent());
    assertThat(
            raftRule.getServers().stream()
                .map(s -> s.getContext().getCommitIndex())
                .distinct()
                .count())
        .isEqualTo(1L);

    final var follower = raftRule.shutdownFollower();

    raftRule.appendEntries(32);

    final var bootstrapFuture = raftRule.bootstrapNodeAsync(follower);
    final var server = raftRule.getServer(follower);

    final Map<Long, State> states = new TreeMap<>();
    server
        .getContext()
        .addStateChangeListener(
            state -> {
              final var commitIdx = server.getContext().getCommitIndex();
              states.put(commitIdx, state);
            });

    bootstrapFuture.join();
    assertThat(states)
        .isEqualTo(Map.ofEntries(Map.entry(1L, State.ACTIVE), Map.entry(33L, State.READY)));
  }

  @Test
  public void shouldCommitEntriesAfterLeaderShutdown() throws Throwable {
    // given
    final var entryCount = 20;
    raftRule.appendEntries(entryCount);
    raftRule.shutdownLeader();

    // when
    raftRule.awaitNewLeader();
    final var lastIndex = raftRule.appendEntries(entryCount);

    // then
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
    final var memberLog = raftRule.getMemberLogs();

    final var maxIndex =
        memberLog.values().stream()
            .flatMap(Collection::stream)
            .map(IndexedRaftLogEntry::index)
            .max(Long::compareTo)
            .orElseThrow();
    assertThat(maxIndex).isEqualTo(lastIndex);
    assertMemberLogs(memberLog);
  }

  @Test
  public void shouldRecoverLeaderRestart() throws Throwable {
    // given
    final var entryCount = 20;
    raftRule.appendEntries(entryCount);
    raftRule.restartLeader();

    // when
    raftRule.awaitNewLeader();
    final var lastIndex = raftRule.appendEntries(entryCount);

    // then
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
    final var memberLog = raftRule.getMemberLogs();

    final var maxIndex =
        memberLog.values().stream()
            .flatMap(Collection::stream)
            .map(IndexedRaftLogEntry::index)
            .max(Long::compareTo)
            .orElseThrow();
    assertThat(maxIndex).isEqualTo(lastIndex);
    assertMemberLogs(memberLog);
  }

  @Test
  public void shouldTakeSnapshot() throws Exception {
    // given
    raftRule.appendEntries(228);

    // when
    raftRule.takeSnapshot(200);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(200)).isTrue();
  }

  @Test
  public void shouldCompactLogOnSnapshot() throws Exception {
    // given
    final var lastIndex = raftRule.appendEntries(228);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
    final var memberLogs = raftRule.getMemberLogs();

    // when
    raftRule.takeCompactingSnapshot(200);

    // then
    final var compactedLogs = raftRule.getMemberLogs();

    assertThat(compactedLogs).isNotEmpty();
    for (final String raftMember : compactedLogs.keySet()) {
      final var compactedLog = compactedLogs.get(raftMember);
      final var previousLog = memberLogs.get(raftMember);
      assertThat(compactedLog).hasSizeLessThan(previousLog.size()).isSubsetOf(previousLog);
    }
  }

  @Test
  public void shouldReplicateSnapshotOnJoin() throws Exception {
    // given
    final var follower = raftRule.shutdownFollower();
    raftRule.appendEntries(128);
    raftRule.takeCompactingSnapshot(200);
    final var leaderSnapshot = raftRule.getSnapshotFromLeader();

    // when
    raftRule.joinCluster(follower);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(200)).isTrue();
    final var snapshot = raftRule.getSnapshotOnNode(follower);

    assertThat(snapshot.getIndex()).isEqualTo(leaderSnapshot.getIndex()).isEqualTo(200);
    assertThat(snapshot.getTerm()).isEqualTo(snapshot.getTerm());
  }

  @Test
  public void shouldReplicateSnapshotWithManyFilesOnJoin() throws Exception {
    // given
    final var follower = raftRule.shutdownFollower();
    raftRule.appendEntries(130);
    final long snapshotIndex = 120;
    raftRule.takeCompactingSnapshot(snapshotIndex, 10);
    final var leaderSnapshot = raftRule.getSnapshotFromLeader();

    // when
    raftRule.joinCluster(follower);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(snapshotIndex)).isTrue();
    final var snapshot = raftRule.getSnapshotOnNode(follower);

    assertThat(snapshot.getIndex()).isEqualTo(leaderSnapshot.getIndex()).isEqualTo(snapshotIndex);
    assertThat(snapshot.getTerm()).isEqualTo(snapshot.getTerm());
  }

  @Test
  public void shouldReplicateEntriesAfterSnapshotOnJoin() throws Exception {
    // given
    final var follower = raftRule.shutdownFollower();
    raftRule.appendEntries(228);
    raftRule.takeCompactingSnapshot(200);

    // when
    LOG.debug("Follower %s joining cluster".formatted(follower));
    raftRule.joinCluster(follower);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(200)).isTrue();

    final var memberLogs = raftRule.getMemberLogs();
    final var entries = memberLogs.get(follower);
    // entries after snapshot should be replicated
    assertThat(entries.get(0).index()).isEqualTo(200 + 1);

    for (final String member : memberLogs.keySet()) {
      if (!follower.equals(member)) {
        final var memberEntries = memberLogs.get(member);
        assertThat(memberEntries).endsWith(entries.toArray(new IndexedRaftLogEntry[0]));
      }
    }
  }

  @Test
  public void shouldReplicateSnapshotAfterDataLoss() throws Exception {
    // given
    raftRule.appendEntries(228);
    raftRule.takeCompactingSnapshot(200);
    final var follower = raftRule.shutdownFollower();
    final var leaderSnapshot = raftRule.getSnapshotFromLeader();

    // when
    raftRule.triggerDataLossOnNode(follower);
    raftRule.bootstrapNode(follower);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(200)).isTrue();
    final var snapshot = raftRule.getSnapshotOnNode(follower);

    assertThat(snapshot.getIndex()).isEqualTo(leaderSnapshot.getIndex()).isEqualTo(200);
    assertThat(snapshot.getTerm()).isEqualTo(leaderSnapshot.getTerm());
    assertThat(snapshot.getId()).isEqualTo(leaderSnapshot.getId());
  }

  @Test
  public void shouldReplicateEntriesAfterDataLoss() throws Exception {
    // given
    raftRule.appendEntries(128);
    final var follower = raftRule.shutdownFollower();

    // when
    raftRule.triggerDataLossOnNode(follower);
    raftRule.bootstrapNode(follower);

    // then
    final var memberLogs = raftRule.getMemberLogs();
    final var entries = memberLogs.get(follower);

    for (final String member : memberLogs.keySet()) {
      if (!follower.equals(member)) {
        final var memberEntries = memberLogs.get(member);
        assertThat(memberEntries).hasSameSizeAs(entries).isEqualTo(entries);
      }
    }
  }

  @Test
  public void shouldReplicateSnapshotMultipleTimesAfterMultipleDataLoss() throws Exception {
    // given
    raftRule.appendEntries(228);
    raftRule.takeCompactingSnapshot(200);
    final var follower = raftRule.shutdownFollower();
    final var leaderSnapshot = raftRule.getSnapshotFromLeader();
    raftRule.triggerDataLossOnNode(follower);
    raftRule.bootstrapNode(follower);

    final var firstSnapshot = raftRule.getSnapshotOnNode(follower);

    // when another data loss happens
    raftRule.shutdownServer(follower);
    raftRule.triggerDataLossOnNode(follower);
    raftRule.bootstrapNode(follower);

    // then snapshot is replicated again
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(200)).isTrue();
    final var newSnapshot = raftRule.getSnapshotOnNode(follower);

    assertThat(newSnapshot.getIndex()).isEqualTo(leaderSnapshot.getIndex()).isEqualTo(200);
    assertThat(newSnapshot.getTerm()).isEqualTo(leaderSnapshot.getTerm());
    assertThat(newSnapshot.getId()).isEqualTo(leaderSnapshot.getId());
    assertThat(newSnapshot).isEqualTo(firstSnapshot);
  }

  @Test
  public void shouldReplicateEntriesAfterSnapshotAfterDataLoss() throws Exception {
    // given
    raftRule.appendEntries(228);
    raftRule.takeCompactingSnapshot(200);
    final var follower = raftRule.shutdownFollower();

    // when
    raftRule.triggerDataLossOnNode(follower);
    raftRule.bootstrapNode(follower);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(200)).isTrue();
    final var memberLogs = raftRule.getMemberLogs();
    final var entries = memberLogs.get(follower);
    // entries after snapshot should be replicated
    assertThat(entries.get(0).index()).isEqualTo(200 + 1);

    for (final String member : memberLogs.keySet()) {
      if (!follower.equals(member)) {
        final var memberEntries = memberLogs.get(member);
        assertThat(memberEntries).endsWith(entries.toArray(new IndexedRaftLogEntry[0]));
      }
    }
  }

  @Test
  public void shouldTakeMultipleSnapshotsAndReplicateSnapshotAfterRestart() throws Exception {
    // given
    raftRule.appendEntries(228);
    raftRule.takeCompactingSnapshot(200);
    final var follower = raftRule.shutdownFollower();
    raftRule.appendEntries(128);
    raftRule.takeCompactingSnapshot(300);
    raftRule.appendEntries(128);
    raftRule.takeCompactingSnapshot(400);
    final var leaderSnapshot = raftRule.getSnapshotFromLeader();

    // when
    raftRule.joinCluster(follower);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(400)).isTrue();
    final var snapshot = raftRule.getSnapshotOnNode(follower);

    assertThat(snapshot.getIndex()).isEqualTo(leaderSnapshot.getIndex()).isEqualTo(400);
    assertThat(snapshot.getTerm()).isEqualTo(snapshot.getTerm());
  }

  @Test
  public void shouldReplicateSnapshotToOldLeaderAfterRestart() throws Exception {
    // given
    raftRule.appendEntries(228);
    raftRule.takeCompactingSnapshot(200);
    final var leader = raftRule.shutdownLeader();
    raftRule.awaitNewLeader();
    raftRule.appendEntries(228);
    raftRule.takeCompactingSnapshot(400);
    final var leaderSnapshot = raftRule.getSnapshotFromLeader();

    // when
    raftRule.joinCluster(leader);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(400)).isTrue();
    final var snapshot = raftRule.getSnapshotOnNode(leader);

    assertThat(snapshot.getIndex()).isEqualTo(leaderSnapshot.getIndex()).isEqualTo(400);
    assertThat(snapshot.getTerm()).isEqualTo(snapshot.getTerm());
  }

  @Test
  public void shouldTruncateLogOnNewerSnapshot() throws Throwable {
    // given
    raftRule.appendEntries(50);
    final var followerB = raftRule.shutdownFollower();
    raftRule.appendEntries(250);
    raftRule.takeCompactingSnapshot(200); // take into account the replication threshold
    // Leader and Follower A Log [200-300].
    // Follower B Log [1-51]

    // when
    // Follower B comes back and receives a snapshot from the leader
    raftRule.joinCluster(followerB);
    raftRule.appendEntries(100);

    // then
    assertThat(raftRule.allNodesHaveSnapshotWithIndex(200)).isTrue();
    final var memberLogs = raftRule.getMemberLogs();
    final var entries = memberLogs.get(followerB);
    // Follower B should have truncated his log to not have any gaps in his log
    // entries after snapshot should be replicated
    assertThat(entries.get(0).index()).isEqualTo(201);
  }

  @Test
  public void shouldNotReplicateSnapshotWhenIndexIsZero() throws Exception {
    // given
    final var follower = raftRule.shutdownFollower();

    raftRule.appendEntries(100);
    raftRule.takeSnapshot(0);

    // expect
    final var leaderSnapshot = raftRule.getSnapshotFromLeader();
    assertThat(leaderSnapshot.getIndex()).isZero();

    // when
    raftRule.joinCluster(follower);

    // then
    final var memberLogs = raftRule.getMemberLogs();
    assertMemberLogs(memberLogs);

    // snapshot is not replicated to follower
    final var followerSnapshotStore = raftRule.getPersistedSnapshotStore(follower);
    assertThat(followerSnapshotStore.getLatestSnapshot()).isNotPresent();
  }

  private void assertMemberLogs(final Map<String, List<IndexedRaftLogEntry>> memberLog) {
    final var members = memberLog.keySet();
    final var iterator = members.iterator();

    if (iterator.hasNext()) {
      final var first = iterator.next();
      final var firstMemberEntries = memberLog.get(first);

      while (iterator.hasNext()) {
        final var otherEntries = memberLog.get(iterator.next());
        assertThat(firstMemberEntries)
            .withFailMessage(memberLog.toString())
            .containsExactly(otherEntries.toArray(new IndexedRaftLogEntry[0]));
      }
    }
  }
}
