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

import io.zeebe.raft.util.InMemoryRaftPersistentStorage;
import io.zeebe.raft.util.RaftClusterRule;
import io.zeebe.raft.util.RaftRule;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RaftPersistentStorageTest
{
    public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
    public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorScheduler);

    public RaftRule raft1 = new RaftRule(actorScheduler, serviceContainerRule, "localhost", 8001, "default", 0);
    public RaftRule raft2 = new RaftRule(actorScheduler, serviceContainerRule,  "localhost", 8002, "default", 0, raft1);

    @Rule
    public RaftClusterRule cluster = new RaftClusterRule(actorScheduler, serviceContainerRule, raft1, raft2);


    @Test
    public void shouldPersistConfiguration()
    {
        // given
        cluster.awaitInitialEventCommittedOnAll(1);
        cluster.awaitRaftEventCommittedOnAll(1);

        // then
        InMemoryRaftPersistentStorage storage = raft1.getPersistentStorage();
        assertThat(storage.getTerm()).isEqualTo(1);
        assertThat(storage.getVotedFor()).isEqualTo(raft1.getSocketAddress());
        assertThat(storage.getMembers()).containsExactly(raft2.getSocketAddress());

        storage = raft2.getPersistentStorage();
        assertThat(storage.getTerm()).isEqualTo(1);
        assertThat(storage.getVotedFor()).isEqualTo(null);
        assertThat(storage.getMembers()).containsExactly(raft1.getSocketAddress());
    }

}
