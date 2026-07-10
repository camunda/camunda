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

import io.atomix.raft.roles.LeaderRole;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.Rule;
import org.junit.Test;

/**
 * Coverage for membership and leadership changes during a coordinated leadership transfer: the exit
 * paths must always complete, never leaving the transfer hanging.
 */
public class RaftLeadershipTransferMembershipTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldReportLeaderChangedWhenLeaderStepsDownDuringCatchUp() throws Exception {
    // given
    raftRule.appendEntries(5);
    final var leader = raftRule.getLeader().orElseThrow();
    final var target = raftRule.getFollower().orElseThrow();
    final var targetId = target.getContext().getCluster().getLocalMember().memberId();
    final long unreachableIndex = leader.getContext().getLog().getLastIndex() + 1_000;

    final CompletableFuture<Optional<LeadershipTransferResult>> catchUp = new CompletableFuture<>();
    leader
        .getContext()
        .getThreadContext()
        .execute(
            () ->
                ((LeaderRole) leader.getContext().getRaftRole())
                    .awaitDesiredLeaderCaughtUp(targetId, unreachableIndex, Duration.ofSeconds(30))
                    .whenComplete(
                        (result, error) -> {
                          if (error != null) {
                            catchUp.completeExceptionally(error);
                          } else {
                            catchUp.complete(result);
                          }
                        }));

    // when
    leader.stepDown().get();

    // then
    assertThat(catchUp)
        .succeedsWithin(Duration.ofSeconds(10))
        .isEqualTo(Optional.of(LeadershipTransferResult.LEADER_CHANGED));
  }
}
