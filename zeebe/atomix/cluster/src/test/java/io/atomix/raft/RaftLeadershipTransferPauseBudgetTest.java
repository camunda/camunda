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
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.TimeoutNowRequest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.Rule;
import org.junit.Test;

/**
 * Coverage for the pause budget spanning every step of a coordinated leadership transfer, using an
 * attempt limit whose promotion budget deliberately outlasts the replication timeout.
 */
public class RaftLeadershipTransferPauseBudgetTest {

  private static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(100);
  private static final Duration REPLICATION_TIMEOUT = Duration.ofSeconds(2);
  private static final int MAX_TRANSFER_ATTEMPTS = 40;

  @Rule
  public RaftRule raftRule =
      RaftRule.withBootstrappedNodes(
          3,
          new RaftRule.Configurator() {
            @Override
            public void configure(final MemberId id, final RaftServer.Builder builder) {
              final var config =
                  new RaftPartitionConfig()
                      .setElectionTimeout(Duration.ofSeconds(3))
                      .setHeartbeatInterval(HEARTBEAT_INTERVAL);
              config.setRebalanceReplicationTimeout(REPLICATION_TIMEOUT);
              config.setRebalanceMaxTransferAttempts(MAX_TRANSFER_ATTEMPTS);
              builder.withPartitionConfig(config);
            }
          });

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
    dropTimeoutNow(leader);

    // when
    final var ack = driver.initiate(target);

    // then
    assertThat(ack.accepted()).isTrue();
    assertThat(driver.reportedResult())
        .succeedsWithin(Duration.ofSeconds(30))
        .extracting(LeadershipTransferResultRequest::result)
        .isEqualTo(LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED);
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);
  }

  private static void dropTimeoutNow(final RaftServer leader) {
    ((TestRaftServerProtocol) leader.getContext().getProtocol())
        .interceptRequest(
            TimeoutNowRequest.class,
            request -> {
              return CompletableFuture.<Void>failedFuture(new RuntimeException("dropped in test"));
            });
  }
}
