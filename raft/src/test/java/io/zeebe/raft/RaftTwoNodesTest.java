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
import io.zeebe.raft.util.*;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class RaftTwoNodesTest
{
    public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
    public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorScheduler);

    public RaftRule raft1 = new RaftRule(serviceContainerRule, "localhost", 8001, "default", 0);
    public RaftRule raft2 = new RaftRule(serviceContainerRule, "localhost", 8002, "default", 0, raft1);

    @Rule
    public RaftClusterRule cluster = new RaftClusterRule(actorScheduler, serviceContainerRule, raft1, raft2);


    @Test
    public void shouldJoinCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();

        // then
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());

        final List<RaftState> raftStateChanges = leader.getRaftStateChanges();
        assertThat(raftStateChanges).contains(RaftState.LEADER);
    }

    @Test
    public void shouldLeaveCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm(), raft1, raft2);

        // when
        final RaftRule[] otherRafts = cluster.getOtherRafts(leader);
        final RaftRule otherRaft = otherRafts[0];
        otherRaft.closeRaft();

        // then
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());

        assertThat(leader.getRaft().getMemberSize()).isEqualTo(0);
    }

    @Test
    public void shouldCommitAfterNodeLeavesCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm(), raft1, raft2);

        final RaftRule[] otherRafts = cluster.getOtherRafts(leader);
        final RaftRule otherRaft = otherRafts[0];
        otherRaft.closeRaft();
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());

        // when
        final EventInfo eventInfo = leader.writeEvents("foo", "bar", "end");

        // then we have single node cluster
        cluster.getRafts().remove(otherRafts[0]);
        cluster.awaitEventCommittedOnAll(eventInfo);
        cluster.awaitEventsCommittedOnAll("foo", "bar", "end");
    }

    @Test
    public void shouldReplicateLogEvents()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();

        // when
        final EventInfo eventInfo = leader.writeEvents("foo", "bar", "end");

        // then
        cluster.awaitEventCommittedOnAll(eventInfo);
        cluster.awaitEventsCommittedOnAll("foo", "bar", "end");
    }

    @Test
    public void shouldNotElectNewLeader() throws InterruptedException
    {
        // given
        final RaftRule oldLeader = cluster.awaitLeader();
        cluster.awaitRaftEventCommittedOnAll(oldLeader.getTerm());

        // when
        cluster.removeRaft(oldLeader);
        Thread.sleep(oldLeader.getConfiguration().getElectionInterval() * 4);

        // then
        final RaftRule follower = cluster.getRafts().get(0);
        assertThat(follower.getState()).isEqualTo(RaftState.FOLLOWER);

        // bring back old leader so that we can leave without timeout
        cluster.registerRaft(oldLeader);
    }

}
