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
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

/** Coverage for the catch-up step of a coordinated leadership transfer. */
public class RaftLeadershipTransferCatchUpTest {

  private static final Duration REPLICATION_TIMEOUT = Duration.ofSeconds(2);

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
                      .setHeartbeatInterval(Duration.ofMillis(100));
              config.setRebalanceReplicationTimeout(REPLICATION_TIMEOUT);
              builder.withPartitionConfig(config);
            }
          });

  @Test
  public void shouldReportTransferredWhenTheDesiredLeaderCatchesUpAndTakesOver() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var driver = new CoordinatedTransferDriver(raftRule, leader);
    final var target = driver.followerOutsideCoordinator();

    // when
    final var ack = driver.initiate(target);

    // then
    assertThat(ack.accepted()).isTrue();
    assertThat(driver.reportedResult())
        .succeedsWithin(Duration.ofSeconds(15))
        .extracting(LeadershipTransferResultRequest::result)
        .isEqualTo(LeadershipTransferResult.TRANSFERRED);
  }

  @Test
  public void shouldReportLeaderChangedWhenLeadershipIsLostWhileCatchingUp() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var driver = new CoordinatedTransferDriver(raftRule, leader);
    final var target = driver.followerOutsideCoordinator();
    raftRule.partition(target);
    raftRule.appendEntries(5);

    // when
    final var ack = driver.initiate(target);
    leader.stepDown().get();

    // then
    assertThat(ack.accepted()).isTrue();
    assertThat(driver.reportedResult())
        .succeedsWithin(Duration.ofSeconds(15))
        .extracting(LeadershipTransferResultRequest::result)
        .isEqualTo(LeadershipTransferResult.LEADER_CHANGED);
  }

  @Test
  public void shouldKeepLeadershipWhenTheDesiredLeaderNeverCatchesUp() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var driver = new CoordinatedTransferDriver(raftRule, leader);
    final var target = driver.followerOutsideCoordinator();
    raftRule.partition(target);
    raftRule.appendEntries(5);

    // when
    final var ack = driver.initiate(target);

    // then
    assertThat(ack.accepted()).isTrue();
    assertThat(driver.reportedResult())
        .succeedsWithin(REPLICATION_TIMEOUT.plusSeconds(15))
        .extracting(LeadershipTransferResultRequest::result)
        .isEqualTo(LeadershipTransferResult.REPLICATION_TIMED_OUT);
    assertThat(leader.getRole()).isEqualTo(Role.LEADER);
  }

  @Test
  public void shouldResumeWritesWhenTheDesiredLeaderNeverCatchesUp() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var driver = new CoordinatedTransferDriver(raftRule, leader);
    final var target = driver.followerOutsideCoordinator();
    raftRule.partition(target);
    raftRule.appendEntries(5);

    // when
    final var ack = driver.initiate(target);
    assertThat(ack.accepted()).isTrue();
    // the result is only reported once the partition has been resumed
    assertThat(driver.reportedResult()).succeedsWithin(REPLICATION_TIMEOUT.plusSeconds(15));

    // then
    assertThatNoException().isThrownBy(raftRule::appendEntry);
  }
}
