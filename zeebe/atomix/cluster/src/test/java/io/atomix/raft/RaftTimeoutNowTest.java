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

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftNamespaces;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.TimeoutNowRequest;
import io.atomix.raft.protocol.TimeoutNowResponse;
import io.atomix.raft.protocol.VoteRequest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class RaftTimeoutNowTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldCampaignImmediatelyAndSkipPreVotePollOnTimeoutNow() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var leaderId = memberId(leader);
    final var targetId = memberId(target);
    final long term = target.getContext().getTerm();

    final var targetProtocol = (TestRaftServerProtocol) target.getContext().getProtocol();
    final LongAdder pollCount = new LongAdder();
    final LongAdder voteCount = new LongAdder();
    targetProtocol.interceptRequest(
        PollRequest.class,
        request -> {
          pollCount.increment();
        });
    targetProtocol.interceptRequest(
        VoteRequest.class,
        request -> {
          voteCount.increment();
        });

    // when
    leader.getContext().getProtocol().timeoutNow(targetId, timeoutNow(term, leaderId));

    // then
    Awaitility.await("target becomes leader")
        .atMost(Duration.ofSeconds(15))
        .until(() -> target.getRole() == Role.LEADER);
    assertThat(voteCount.sum()).as("target sent vote requests").isPositive();
    assertThat(pollCount.sum()).as("target skipped the pre-vote poll").isZero();
  }

  @Test
  public void shouldRejectTimeoutNowThatIsNotFromCurrentLeader() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);
    final long term = target.getContext().getTerm();
    final var knownLeaderBefore = knownLeaderId(target);

    // when
    final var response =
        leader
            .getContext()
            .getProtocol()
            .timeoutNow(targetId, timeoutNow(term, MemberId.from("not-the-leader")))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(response.status()).isEqualTo(Status.ERROR);
    assertThat(target.getRole()).isEqualTo(Role.FOLLOWER);
    assertThat(target.getContext().getTerm()).isEqualTo(term);
    assertThat(knownLeaderId(target)).isEqualTo(knownLeaderBefore);
  }

  @Test
  public void shouldRejectFutureTermTimeoutNow() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var leaderId = memberId(leader);
    final var targetId = memberId(target);
    final long term = target.getContext().getTerm();
    final long futureTerm = term + 1;
    final var knownLeaderBefore = knownLeaderId(target);

    // when
    final var response =
        leader
            .getContext()
            .getProtocol()
            .timeoutNow(targetId, timeoutNow(futureTerm, leaderId))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(response.status()).isEqualTo(Status.ERROR);
    assertThat(target.getRole()).isEqualTo(Role.FOLLOWER);
    assertThat(target.getContext().getTerm()).isEqualTo(term);
    assertThat(knownLeaderId(target)).isEqualTo(knownLeaderBefore);
  }

  @Test
  public void shouldRejectStaleTimeoutNow() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var leaderId = memberId(leader);
    final var targetId = memberId(target);
    final long term = target.getContext().getTerm();
    final long staleTerm = term - 1;
    final var knownLeaderBefore = knownLeaderId(target);

    // when
    final var response =
        leader
            .getContext()
            .getProtocol()
            .timeoutNow(targetId, timeoutNow(staleTerm, leaderId))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(response.status()).isEqualTo(Status.ERROR);
    assertThat(target.getRole()).isEqualTo(Role.FOLLOWER);
    assertThat(target.getContext().getTerm()).isEqualTo(term);
    assertThat(knownLeaderId(target)).isEqualTo(knownLeaderBefore);
  }

  @Test
  public void shouldRejectTimeoutNowWhenNotAFollower() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var follower = raftRule.getFollower().orElseThrow();
    final var leaderId = memberId(leader);
    final long term = leader.getContext().getTerm();

    // when
    final var response =
        follower
            .getContext()
            .getProtocol()
            .timeoutNow(leaderId, timeoutNow(term, leaderId))
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(response.status()).isEqualTo(Status.ERROR);
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);
    assertThat(leader.getContext().getTerm()).isEqualTo(term);
  }

  @Test
  public void shouldRoundTripTimeoutNowMessagesThroughRaftNamespace() {
    // given
    final var request = timeoutNow(7, MemberId.from("2"));
    final var response = TimeoutNowResponse.builder().withStatus(Status.OK).build();

    // when
    final TimeoutNowRequest deserializedRequest =
        RaftNamespaces.RAFT_PROTOCOL.deserialize(RaftNamespaces.RAFT_PROTOCOL.serialize(request));
    final TimeoutNowResponse deserializedResponse =
        RaftNamespaces.RAFT_PROTOCOL.deserialize(RaftNamespaces.RAFT_PROTOCOL.serialize(response));

    // then
    assertThat(deserializedRequest).isEqualTo(request);
    assertThat(deserializedResponse.status()).isEqualTo(Status.OK);
  }

  @Test
  public void shouldKeepSenderAndReceiverHealthyWhenATimeoutNowIsUnprocessable() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = memberId(target);
    final var targetTermBefore = target.getContext().getTerm();

    final var leaderProtocol = (TestRaftServerProtocol) leader.getContext().getProtocol();
    final Function<TimeoutNowRequest, CompletableFuture<Void>> failRequest =
        request ->
            CompletableFuture.failedFuture(
                new RuntimeException("simulated: receiver cannot handle TimeoutNow"));
    leaderProtocol.interceptRequest(TimeoutNowRequest.class, failRequest);

    // when
    final var response =
        leader
            .getContext()
            .getProtocol()
            .timeoutNow(targetId, timeoutNow(targetTermBefore, memberId(leader)));

    // then
    assertThat(response).failsWithin(Duration.ofSeconds(5));
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);

    assertThat(target.getRole()).isEqualTo(Role.FOLLOWER);
    assertThat(target.getContext().getTerm()).isEqualTo(targetTermBefore);
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }

  private static MemberId knownLeaderId(final RaftServer server) {
    return server.getContext().getLeader().memberId();
  }

  private static TimeoutNowRequest timeoutNow(final long term, final MemberId leader) {
    return TimeoutNowRequest.builder().withTerm(term).withLeader(leader).build();
  }
}
