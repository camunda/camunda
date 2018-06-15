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

import java.util.List;

import io.zeebe.raft.state.RaftState;
import io.zeebe.raft.util.EventInfo;
import io.zeebe.raft.util.RaftClusterRule;
import io.zeebe.raft.util.RaftRule;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class RaftSingleNodeTest
{
    public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();

    public ServiceContainerRule serviceContainer = new ServiceContainerRule(actorScheduler);

    public RaftRule raft1 = new RaftRule(serviceContainer, "localhost", 8001, "default", 0);

    @Rule
    public RaftClusterRule cluster = new RaftClusterRule(actorScheduler, serviceContainer, raft1);

    @Test
    public void shouldJoinCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();

        // then
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());

        final List<RaftState> raftStateChanges = leader.getRaftStateChanges();
        assertThat(raftStateChanges).contains(RaftState.LEADER);
    }


    @Test
    public void shouldCommitEntries()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();

        // when
        final EventInfo eventInfo = leader.writeEvents("foo", "bar", "end");

        // then
        cluster.awaitEventCommittedOnAll(eventInfo);
        cluster.awaitEventsCommittedOnAll("foo", "bar", "end");
    }

}
