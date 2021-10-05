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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public class SnapshotReplicationListenerTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldNotifySnapshotReplicationListener() throws Throwable {
    // given
    final var snapshotReplicationListener = mock(SnapshotReplicationListener.class);
    final var follower = raftRule.getFollower().orElseThrow();
    follower.getContext().addSnapshotReplicationListener(snapshotReplicationListener);
    raftRule.partition(follower);

    final var commitIndex = raftRule.appendEntries(2); // awaits commit
    final var leader = raftRule.getLeader().orElseThrow();
    raftRule.doSnapshotOnMember(leader, commitIndex, 1);
    raftRule.appendEntry();

    // when
    // leader appended new entries and took snapshot when the follower was disconnected. When
    // follower reconnects, it should receive a new new snapshot which resets the log.
    final var snapshotReceived = new CountDownLatch(1);
    raftRule
        .getPersistedSnapshotStore(follower.name())
        .addSnapshotListener(s -> snapshotReceived.countDown());
    raftRule.reconnect(follower);

    assertThat(snapshotReceived.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    verify(snapshotReplicationListener, timeout(1_000).times(1)).onSnapshotReplicationStarted();
    verify(snapshotReplicationListener, timeout(1_000).times(1))
        .onSnapshotReplicationCompleted(follower.getTerm());
  }
}
