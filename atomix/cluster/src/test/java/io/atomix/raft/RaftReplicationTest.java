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
    raftRule.doSnapshotOnMemberWithoutCompaction(leader, snapshotIndex, 1);
    raftRule.reconnect(follower);

    // then
    assertThat(follower.getContext().getPersistedSnapshotStore().getCurrentSnapshotIndex())
        .isNotEqualTo(snapshotIndex);
    raftRule.awaitSameLogSizeOnAllNodes(lastCommitIndex);
  }

  @Test
  public void shouldReplicateSnapshotIfEventsNotAvailable() throws Exception {
    // given
    final var entryCount = 10;
    final var snapshotIndex = 15;

    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();

    raftRule.appendEntries(entryCount);

    // when
    raftRule.partition(follower);
    final var lastCommitIndex = raftRule.appendEntries(entryCount);

    // Snapshot multiple chunks to split snapshot replication across multiple messages
    raftRule.doSnapshotOnMember(leader, snapshotIndex, 3);

    raftRule.reconnect(follower);

    // then - follower received snapshot
    raftRule.awaitSameLogSizeOnAllNodes(lastCommitIndex);
    assertThat(follower.getContext().getPersistedSnapshotStore().getCurrentSnapshotIndex())
        .isEqualTo(snapshotIndex);
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
    raftRule.doSnapshotOnMemberWithoutCompaction(leader, snapshotIndex, 1);

    raftRule.reconnect(follower);

    // then - follower received snapshot
    raftRule.awaitSameLogSizeOnAllNodes(lastCommitIndex);
    assertThat(follower.getContext().getPersistedSnapshotStore().getCurrentSnapshotIndex())
        .isEqualTo(snapshotIndex);
  }
}
