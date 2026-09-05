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
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.TimeoutNowRequest;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Coverage for the leader-side rebalance settings (and overrides). */
@RunWith(Parameterized.class)
public class RaftLeadershipTransferConfigurationTest {

  private static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(100);
  private static final Duration ELECTION_TIMEOUT = Duration.ofSeconds(3);
  private static final long LAG_THRESHOLD = 4096;
  private static final Duration REPLICATION_TIMEOUT = Duration.ofSeconds(2);
  private static final int MAX_TRANSFER_ATTEMPTS = 40;

  private static final long CONFLICTING_LAG_THRESHOLD = LAG_THRESHOLD * 1000;
  private static final Duration CONFLICTING_REPLICATION_TIMEOUT = Duration.ofSeconds(30);
  private static final int CONFLICTING_MAX_TRANSFER_ATTEMPTS = 5;
  @Rule public final RaftRule raftRule;
  private final Consumer<LeadershipTransferInitiateRequest.Builder> requestOverrides;

  public RaftLeadershipTransferConfigurationTest(
      final String caseName,
      final RaftRule.Configurator configurator,
      final Consumer<LeadershipTransferInitiateRequest.Builder> requestOverrides) {
    raftRule = RaftRule.withBootstrappedNodes(3, configurator);
    this.requestOverrides = requestOverrides;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> cases() {
    return List.of(
        new Object[] {
          "configured values",
          partitionConfigurator(LAG_THRESHOLD, REPLICATION_TIMEOUT, MAX_TRANSFER_ATTEMPTS),
          (Consumer<LeadershipTransferInitiateRequest.Builder>) builder -> {}
        },
        new Object[] {
          "command overrides",
          partitionConfigurator(
              CONFLICTING_LAG_THRESHOLD,
              CONFLICTING_REPLICATION_TIMEOUT,
              CONFLICTING_MAX_TRANSFER_ATTEMPTS),
          (Consumer<LeadershipTransferInitiateRequest.Builder>)
              builder ->
                  builder
                      .withReplicationLagThreshold(LAG_THRESHOLD)
                      .withReplicationTimeout(REPLICATION_TIMEOUT)
                      .withMaxTransferAttempts(MAX_TRANSFER_ATTEMPTS)
        });
  }

  @Test
  public void shouldRejectTransferWhenLagTooHigh() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var driver = new CoordinatedTransferDriver(raftRule, leader);
    final var target = driver.followerOutsideCoordinator();
    setReplicationLag(leader, memberId(target), LAG_THRESHOLD + 1);

    // when
    final var ack = driver.initiate(target, requestOverrides);

    // then
    assertThat(ack.rejectionReason()).isEqualTo(LeadershipTransferResult.LAG_TOO_HIGH);
  }

  @Test
  public void shouldSendExactlyMaxTransferAttemptsThenGiveUp() throws Exception {
    // given
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var driver = new CoordinatedTransferDriver(raftRule, leader);
    final var target = driver.followerOutsideCoordinator();
    awaitCaughtUp(leader, memberId(target));
    final var sends = dropTimeoutNow(leader);

    // when
    final var ack = driver.initiate(target, requestOverrides);

    // then
    assertThat(ack.accepted()).isTrue();
    assertThat(driver.reportedResult())
        .succeedsWithin(Duration.ofSeconds(15))
        .extracting(LeadershipTransferResultRequest::result)
        .isEqualTo(LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED);
    assertThat(sends.sum())
        .as("sends are bounded by the effective max attempts")
        .isEqualTo(MAX_TRANSFER_ATTEMPTS);
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);
  }

  @Test
  public void shouldKeepLeadershipWhenPromotionOutlastsTheReplicationTimeout() throws Exception {
    // given
    assertThat(HEARTBEAT_INTERVAL.multipliedBy(MAX_TRANSFER_ATTEMPTS))
        .as("promotion alone lasts longer than the replication timeout")
        .isGreaterThan(REPLICATION_TIMEOUT);
    raftRule.appendEntries(10);
    final var leader = raftRule.getLeader().orElseThrow();
    final var driver = new CoordinatedTransferDriver(raftRule, leader);
    final var target = driver.followerOutsideCoordinator();
    awaitCaughtUp(leader, memberId(target));
    final var sends = dropTimeoutNow(leader);

    // when
    final var ack = driver.initiate(target, requestOverrides);

    // then
    assertThat(ack.accepted()).isTrue();
    assertThat(driver.reportedResult())
        .succeedsWithin(Duration.ofSeconds(30))
        .extracting(LeadershipTransferResultRequest::result)
        .isEqualTo(LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED);
    assertThat(sends.sum())
        .as("the promotion budget ran for the effective max attempts, not the conflicting default")
        .isEqualTo(MAX_TRANSFER_ATTEMPTS);
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);
  }

  private static RaftRule.Configurator partitionConfigurator(
      final long lagThreshold, final Duration replicationTimeout, final int maxTransferAttempts) {
    return new RaftRule.Configurator() {
      @Override
      public void configure(final MemberId id, final RaftServer.Builder builder) {
        final var config =
            new RaftPartitionConfig()
                .setElectionTimeout(ELECTION_TIMEOUT)
                .setHeartbeatInterval(HEARTBEAT_INTERVAL);
        config.setRebalanceReplicationLagThreshold(lagThreshold);
        config.setRebalanceReplicationTimeout(replicationTimeout);
        config.setRebalanceMaxTransferAttempts(maxTransferAttempts);
        builder.withPartitionConfig(config);
      }
    };
  }

  private static void awaitCaughtUp(final RaftServer leader, final MemberId member) {
    Awaitility.await("until " + member + " has acknowledged every append")
        .atMost(Duration.ofSeconds(30))
        .until(() -> replicationLag(leader, member) == 0);
  }

  private static long replicationLag(final RaftServer leader, final MemberId member)
      throws Exception {
    final var lag = new CompletableFuture<Long>();
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () ->
                lag.complete(
                    leader
                        .getContext()
                        .getCluster()
                        .getMemberContext(member)
                        .getReplicationLagBytes()));
    return lag.get(10, TimeUnit.SECONDS);
  }

  private static void setReplicationLag(
      final RaftServer leader, final MemberId member, final long lagBytes) throws Exception {
    final var done = new CompletableFuture<Void>();
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () -> {
              leader
                  .getContext()
                  .getCluster()
                  .getMemberContext(member)
                  .setSnapshotReplicationLag(lagBytes);
              done.complete(null);
            });
    done.get(10, TimeUnit.SECONDS);
  }

  private static LongAdder dropTimeoutNow(final RaftServer leader) {
    final var sends = new LongAdder();
    ((TestRaftServerProtocol) leader.getContext().getProtocol())
        .interceptRequest(
            TimeoutNowRequest.class,
            request -> {
              sends.increment();
              return CompletableFuture.failedFuture(new RuntimeException("dropped in test"));
            });
    return sends;
  }

  private static MemberId memberId(final RaftServer server) {
    return server.getContext().getCluster().getLocalMember().memberId();
  }
}
