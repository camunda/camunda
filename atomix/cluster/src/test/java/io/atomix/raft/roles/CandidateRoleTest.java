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
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.ControllableRaftContexts;
import io.atomix.raft.RaftServer.Role;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class CandidateRoleTest {
  private ControllableRaftContexts raftContexts;

  @TempDir private Path raftDataDirectory;

  @BeforeEach
  public void before() throws Exception {
    raftContexts = new ControllableRaftContexts(3);
    raftContexts.setup(raftDataDirectory, new Random(1));
  }

  @AfterEach
  public void shutdown() throws IOException {
    raftContexts.shutdown();
  }

  // Regression "https://github.com/camunda/zeebe/issues/11665"
  @Test
  void shouldTransitionToFollowerWhenElectionTimesOut() {
    // given
    final var chosenCandidate = 0; // chose any member as candidate
    // Timeout on chosen candidate so that it can start election before other members
    raftContexts.tickElectionTimeout(chosenCandidate);
    raftContexts.tickHeartbeatTimeout(chosenCandidate);

    // wait until chosen member becomes a candidate
    int steps = 100;
    while (!isCandidate(chosenCandidate)) {
      raftContexts.tickHeartbeatTimeout();
      raftContexts.processAllMessage();
      raftContexts.runUntilDone();
      if (steps-- < 0) {
        break;
      }
    }

    assertThat(isCandidate(chosenCandidate)).isTrue();

    // when

    // Allow enough time to run two rounds of vote request
    steps = 100;
    while (isCandidate(chosenCandidate)) {
      raftContexts.tickHeartbeatTimeout(chosenCandidate);
      // Other members do nothing so that the vote requests from the candidate can timeout
      if (steps-- < 0) {
        break;
      }
    }

    // then
    assertThat(raftContexts.getRaftContext(chosenCandidate).getRole()).isEqualTo(Role.FOLLOWER);
  }

  private boolean isCandidate(final int expectedCandidate) {
    return raftContexts.getRaftContext(expectedCandidate).getRole() == Role.CANDIDATE;
  }
}
