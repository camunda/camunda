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

import io.atomix.raft.RaftServer.Role;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RaftReplicationTest {
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
  public void shouldPreferReplicatingEvents() throws Exception {
    // given
    final var entryCount = 10;
    final var snapshotIndex = 15;

    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();

    raftRule.appendEntries(entryCount);

    // when
    raftRule.partition(follower);
    final var lastCommitIndex = raftRule.appendEntries(entryCount);
    raftRule.takeSnapshot(leader, snapshotIndex, 1);
    raftRule.reconnect(follower);

    // then
    assertThat(follower.getContext().getPersistedSnapshotStore().getCurrentSnapshotIndex())
        .isNotEqualTo(snapshotIndex);
    raftRule.awaitSameLogSizeOnAllNodes(lastCommitIndex);
  }

  @Test
  public void shouldReplicateSnapshotIfEventsNotAvailable() throws Exception {
    // given
    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();

    raftRule.appendEntries(50);

    // when
    raftRule.partition(follower);
    final var lastCommitIndex = raftRule.appendEntries(200);

    // Snapshot multiple chunks to split snapshot replication across multiple messages
    raftRule.takeCompactingSnapshot(leader, 200, 3);

    raftRule.reconnect(follower);

    // then - follower received snapshot
    raftRule.awaitSameLogSizeOnAllNodes(lastCommitIndex);
    assertThat(follower.getContext().getPersistedSnapshotStore().getCurrentSnapshotIndex())
        .isEqualTo(200);
  }

  @Test
  public void shouldReplicateSnapshotIfMemberLagAboveThreshold() throws Exception {
    // given
    final var entryCount = 10;
    final var snapshotIndex = 15;

    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();

    raftRule.appendEntries(entryCount);

    // when
    raftRule.partition(follower);
    final var lastCommitIndex = raftRule.appendEntries(entryCount);

    // Set threshold to a smaller, sensible value
    leader.getContext().setPreferSnapshotReplicationThreshold(1);
    // since compaction is asynchronous, in all likelihood it will not run before the test
    // finishes, but that's not guaranteed
    raftRule.takeSnapshot(leader, snapshotIndex, 1);
    raftRule.reconnect(follower);

    // then - follower received snapshot
    raftRule.awaitSameLogSizeOnAllNodes(lastCommitIndex);
    // the default storage config allows writing about 9 entries per segment; if we trigger a
    // snapshot at index 15, we know there are some entries left, but let's assert it anyway in case
    // this is ever false and the test must be updated
    assertThat(raftRule.getLeader().orElseThrow().getContext().getLog().getFirstIndex())
        .isLessThan(snapshotIndex);
    assertThat(follower.getContext().getPersistedSnapshotStore().getCurrentSnapshotIndex())
        .isEqualTo(snapshotIndex);
  }

  @Test
  // Regression test for https://github.com/camunda/camunda/issues/9820
  public void shouldNotGetStuckInSnapshotReplicationLoop() throws Exception {
    // given -- a cluster where follower's log starts at snapshot index
    final var initialLeader = raftRule.getLeader().orElseThrow();

    final var snapshotIndex = raftRule.appendEntries(5);
    raftRule.takeSnapshot(initialLeader, snapshotIndex, 3);
    raftRule.appendEntries(5);

    raftRule.getServers().stream()
        .filter(s -> s.getRole() == Role.FOLLOWER)
        .toList()
        .forEach(
            (follower) -> {
              try {
                raftRule.shutdownServer(follower);
                // force data loss so that follower must receive the snapshot & has no preceding log
                raftRule.triggerDataLossOnNode(follower.name());
                raftRule.joinCluster(follower.name());
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            });

    // when -- letting the initial leader re-join after data loss
    raftRule.shutdownServer(initialLeader);
    raftRule.awaitNewLeader();
    raftRule.triggerDataLossOnNode(initialLeader.name());
    raftRule.joinCluster(initialLeader.name());

    // then -- all members should have snapshot and latest log
    raftRule.allNodesHaveSnapshotWithIndex(snapshotIndex);
    Awaitility.await("All members should have the latest log")
        .until(
            () ->
                raftRule.getServers().stream()
                        .map(s -> s.getContext().getLog().getLastIndex())
                        .collect(Collectors.toSet())
                        .size()
                    == 1);
  }
}
