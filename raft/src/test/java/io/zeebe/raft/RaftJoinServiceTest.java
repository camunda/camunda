/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.raft.state.RaftState;
import io.zeebe.raft.util.RaftClusterRule;
import io.zeebe.raft.util.RaftRule;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class RaftJoinServiceTest {
  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

  public RaftRule raft1 = new RaftRule(serviceContainerRule, 1, 0);
  public RaftRule raft2 = new RaftRule(serviceContainerRule, 2, 0, raft1);

  // Do not add raft 1 to cluster rule so raft 2 is never able to join
  @Rule
  public RaftClusterRule cluster =
      new RaftClusterRule(actorSchedulerRule, serviceContainerRule, raft2);

  @Test
  public void shouldBeInterruptedIfShutdownDuringStart() {
    // given
    cluster.awaitRaftState(raft2, RaftState.FOLLOWER);

    // when
    raft2.closeRaft();

    // then
    assertThat(raft2.isClosed()).isTrue();
    assertThat(raft2.isJoined()).isFalse();
  }
}
