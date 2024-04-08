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

import io.atomix.raft.RaftError.Type;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.TestRaftServerProtocol.ResponseInterceptor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RaftSnapshotReplicationFailureHandlingTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);
  private RaftServer follower;
  private AtomicInteger totalInstallRequest;
  private TestRaftServerProtocol leaderProtocol;
  private RaftServer leader;

  @Before
  public void setup() {
    leader = raftRule.getLeader().orElseThrow();
    leaderProtocol = (TestRaftServerProtocol) leader.getContext().getProtocol();
    totalInstallRequest = new AtomicInteger(0);
    leaderProtocol.interceptRequest(
        InstallRequest.class,
        (Consumer<InstallRequest>) (request) -> totalInstallRequest.incrementAndGet());
  }

  @Test
  public void shouldNotRestartFromFirstChunkWhenInstallRequestTimesOut() throws Throwable {
    // given
    final int numberOfChunks = 10;
    disconnectFollowerAndTakeSnapshot(numberOfChunks);

    // Time out request after sending a request. This simulates the behaviour that the receiver has
    // processed the request, but the request timeout out at the sender.
    leaderProtocol.interceptResponse(
        InstallResponse.class, new TimingOutResponseInterceptor(numberOfChunks - 1));

    // when
    reconnectFollowerAndAwaitSnapshot();

    // then
    assertThat(totalInstallRequest.get())
        .describedAs("Should only resend one snapshot chunk")
        // Before follower reconnects, sometimes leader sends an InstallRequest which
        // ends up in connect exception
        .isLessThan(numberOfChunks + 3);
  }

  @Test
  public void shouldRestartSnapshotReplicationIfFollowerRejectedRequest() throws Throwable {
    // given
    final int numberOfChunks = 10;
    disconnectFollowerAndTakeSnapshot(numberOfChunks);

    leaderProtocol.interceptResponse(
        InstallResponse.class, new RejectingInterceptor(numberOfChunks - 1));

    // when
    reconnectFollowerAndAwaitSnapshot();

    // then
    assertThat(totalInstallRequest.get())
        .describedAs("Should resent chunks from 0 to 8")
        // Before follower reconnects, sometimes leader sends an InstallRequest which
        // ends up in connect exception
        .isLessThan(2 * numberOfChunks + 1);
  }

  @Test
  public void shouldResentSnapshotIfFirstChunkTimedOut() throws Throwable {
    // given
    final int numberOfChunks = 1;
    disconnectFollowerAndTakeSnapshot(numberOfChunks);

    // Time out request before sending it to the follower. This is required for deterministic test.
    final var requestInterceptor = new TimingOutRequestInterceptor(1);
    leaderProtocol.interceptRequest(InstallRequest.class, requestInterceptor);

    // when
    reconnectFollowerAndAwaitSnapshot();

    // then
    assertThat(requestInterceptor.getCount())
        .describedAs("Should resent snapshot chunk")
        .isEqualTo(2);
  }

  private void reconnectFollowerAndAwaitSnapshot() throws InterruptedException {
    final var snapshotReceived = new CountDownLatch(1);
    raftRule
        .getPersistedSnapshotStore(follower.name())
        .addSnapshotListener(s -> snapshotReceived.countDown());
    raftRule.reconnect(follower);

    assertThat(snapshotReceived.await(30, TimeUnit.SECONDS)).isTrue();
  }

  private void disconnectFollowerAndTakeSnapshot(final int numberOfChunks) throws Exception {
    follower = raftRule.getFollower().orElseThrow();
    raftRule.partition(follower);

    leader.getContext().setPreferSnapshotReplicationThreshold(1);
    final var commitIndex = raftRule.appendEntries(2); // awaits commit

    raftRule.takeSnapshot(leader, commitIndex, numberOfChunks);
    raftRule.appendEntry();
  }

  private static class TimingOutResponseInterceptor
      implements ResponseInterceptor<InstallResponse> {
    private int count = 0;
    private final int timeoutAtRequest;

    public TimingOutResponseInterceptor(final int timeoutAtRequest) {
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

    int getCount() {
      return count;
    }
  }

  private static class TimingOutRequestInterceptor
      implements Function<InstallRequest, CompletableFuture<Void>> {
    private int count = 0;
    private final int timeoutAtRequest;

    public TimingOutRequestInterceptor(final int timeoutAtRequest) {
      this.timeoutAtRequest = timeoutAtRequest;
    }

    int getCount() {
      return count;
    }

    @Override
    public CompletableFuture<Void> apply(final InstallRequest installRequest) {
      count++;
      if (count == timeoutAtRequest) {
        return CompletableFuture.failedFuture(new TimeoutException());
      } else {
        return CompletableFuture.completedFuture(null);
      }
    }
  }

  private static class RejectingInterceptor implements ResponseInterceptor<InstallResponse> {
    private int count = 0;
    private final int rejectAtChunk;

    public RejectingInterceptor(final int rejectAtChunk) {
      this.rejectAtChunk = rejectAtChunk;
    }

    @Override
    public CompletableFuture<InstallResponse> apply(final InstallResponse installResponse) {
      count++;
      if (count == rejectAtChunk) {
        final var rejectionResponse =
            InstallResponse.builder()
                .withError(Type.PROTOCOL_ERROR)
                .withStatus(InstallResponse.Status.ERROR)
                .build();
        return CompletableFuture.completedFuture(rejectionResponse);
      } else {
        return CompletableFuture.completedFuture(installResponse);
      }
    }
  }
}
