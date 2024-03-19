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

import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.TestRaftServerProtocol.ResponseInterceptor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;

public class RaftSnapshotReplicationTimeoutTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldNotRestartFromFirstChunkWhenInstallRequestTimesOut() throws Throwable {
    // given
    final var follower = raftRule.getFollower().orElseThrow();
    raftRule.partition(follower);

    final var leader = raftRule.getLeader().orElseThrow();
    leader.getContext().setPreferSnapshotReplicationThreshold(1);
    final var commitIndex = raftRule.appendEntries(2); // awaits commit

    final int numberOfChunks = 10;
    raftRule.takeSnapshot(leader, commitIndex, numberOfChunks);
    raftRule.appendEntry();

    final TestRaftServerProtocol leaderProtocol =
        (TestRaftServerProtocol) leader.getContext().getProtocol();
    final AtomicInteger totalInstallRequest = new AtomicInteger(0);
    leaderProtocol.interceptRequest(
        InstallRequest.class, (request) -> totalInstallRequest.incrementAndGet());
    leaderProtocol.interceptResponse(
        InstallResponse.class, new TimingOutInterceptor(numberOfChunks - 1));

    // when
    // leader appended new entries and took snapshot when the follower was disconnected. When
    // follower reconnects, it should receive a new snapshot.
    final var snapshotReceived = new CountDownLatch(1);
    raftRule
        .getPersistedSnapshotStore(follower.name())
        .addSnapshotListener(s -> snapshotReceived.countDown());
    raftRule.reconnect(follower);

    assertThat(snapshotReceived.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    // Total 10 chunks + 1 retry
    assertThat(totalInstallRequest.get()).isEqualTo(numberOfChunks + 1);
  }

  private static class TimingOutInterceptor implements ResponseInterceptor<InstallResponse> {
    private int count = 0;
    private final int timeoutAtRequest;

    public TimingOutInterceptor(final int timeoutAtRequest) {
      this.timeoutAtRequest = timeoutAtRequest;
    }

    @Override
    public CompletableFuture<InstallResponse> apply(final InstallResponse installResponse) {
      count++;
      if (count == timeoutAtRequest) {
        return CompletableFuture.failedFuture(new TimeoutException());
      } else {
        return CompletableFuture.completedFuture(installResponse);
      }
    }
  }
}
