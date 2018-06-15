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
import org.junit.*;

public class RaftFiveNodesTest
{
    public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
    public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorScheduler);

    public RaftRule raft1 = new RaftRule(serviceContainerRule, "localhost", 8001, "default", 0);
    public RaftRule raft2 = new RaftRule(serviceContainerRule, "localhost", 8002, "default", 0, raft1);
    public RaftRule raft3 = new RaftRule(serviceContainerRule, "localhost", 8003, "default", 0, raft2);
    public RaftRule raft4 = new RaftRule(serviceContainerRule, "localhost", 8004, "default", 0, raft2, raft3);
    public RaftRule raft5 = new RaftRule(serviceContainerRule, "localhost", 8005, "default", 0, raft3);

    @Rule
    public RaftClusterRule cluster = new RaftClusterRule(actorScheduler, serviceContainerRule, raft1, raft2, raft3, raft4, raft5);

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
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm(), raft1, raft2, raft3, raft4, raft5);

        // when
        final RaftRule[] otherRafts = cluster.getOtherRafts(leader);
        final RaftRule otherRaft = otherRafts[0];
        otherRaft.closeRaft();

        // then
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());
        assertThat(leader.getRaft().getMemberSize()).isEqualTo(3);
    }

    @Test
    public void shouldCommitAfterNodeLeavesCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm(), raft1, raft2, raft3, raft4, raft5);
        final RaftRule[] otherRafts = cluster.getOtherRafts(leader);
        final RaftRule otherRaft = otherRafts[0];
        otherRaft.closeRaft();
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());

        // when
        final EventInfo eventInfo = leader.writeEvents("foo", "bar", "end");

        // then
        cluster.getRafts().remove(otherRafts[0]);
        cluster.awaitEventCommittedOnAll(eventInfo);
        cluster.awaitEventsCommittedOnAll("foo", "bar", "end");
    }

    @Test
    public void shouldCommitAfterOldQuorumLeavesClusterClean()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm(), raft1, raft2, raft3, raft4, raft5);

        final RaftRule[] otherRafts = cluster.getOtherRafts(leader);
        final int quorum = leader.getRaft().requiredQuorum();
        for (int i = 0; i < quorum; i++)
        {
            final RaftRule raftRule = otherRafts[i];
            raftRule.closeRaft();
            cluster.getRafts().remove(raftRule);
        }

        // when
        final EventInfo eventInfo = leader.writeEvents("foo", "bar", "end");

        // then
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
    public void shouldElectNewLeader()
    {
        // given
        final RaftRule oldLeader = cluster.awaitLeader();
        cluster.awaitRaftEventCommittedOnAll(oldLeader.getTerm());

        // when
        cluster.removeRaft(oldLeader);

        // then
        final RaftRule newLeader = cluster.awaitLeader();
        assertThat(newLeader)
            .isNotNull()
            .isNotEqualTo(raft1);

        // when
        final EventInfo eventInfo = newLeader.writeEvents("foo", "bar", "end");

        // then
        cluster.awaitEventCommittedOnAll(eventInfo);
        cluster.awaitEventsCommittedOnAll("foo", "bar", "end");
    }

    @Test
    public void shouldRejoinCluster()
    {
        // given
        final RaftRule oldLeader = cluster.awaitLeader();

        EventInfo eventInfo = oldLeader.writeEvents("foo", "bar");
        cluster.awaitEventCommittedOnAll(eventInfo);

        // when leader leaves the cluster
        cluster.removeRaft(oldLeader);

        // and a new leader writes more events
        final RaftRule newLeader = cluster.awaitLeader();

        eventInfo = newLeader.writeEvents("hello", "world");
        cluster.awaitEventCommittedOnAll(eventInfo);

        // and the old leader rejoins the cluster
        cluster.registerRaft(oldLeader);

        // then the new events are also committed on the old leader
        cluster.awaitEventCommitted(oldLeader, eventInfo);
        cluster.awaitEventsCommittedOnAll("foo", "bar", "hello", "world");
    }

    @Test
    public void shouldTruncateLog()
    {
        // given a log with two events committed
        final RaftRule oldLeader = cluster.awaitLeader();
        cluster.awaitRaftEventCommittedOnAll(oldLeader.getTerm());

        EventInfo eventInfo = oldLeader.writeEvents("foo", "bar");
        cluster.awaitEventCommittedOnAll(eventInfo);
        cluster.awaitRaftEventCommittedOnAll(oldLeader.getTerm());

        // when a quorum leaves the cluster
        final RaftRule[] otherRafts = cluster.getOtherRafts(oldLeader);
        cluster.removeRafts(otherRafts);

        // and more events are written
        eventInfo = oldLeader.writeEvents("hello", "world");
        cluster.awaitEventAppendedOnAll(eventInfo);

        // and leader and remaining leaves cluster
        cluster.removeRafts(oldLeader);

        // and quorum returns
        cluster.registerRafts(otherRafts);

        // and a new leader writes more events
        final RaftRule newLeader = cluster.awaitLeader();

        eventInfo = newLeader.writeEvents("oh", "boy");
        cluster.awaitEventCommittedOnAll(eventInfo);

        // and the nodes with the extended older log rejoins the cluster
        cluster.registerRaft(oldLeader);

        // then the new events are also committed on the returning nodes discarding there uncommitted events
        cluster.awaitInitialEventCommittedOnAll(newLeader.getTerm());
        cluster.awaitEventCommittedOnAll(eventInfo);
        cluster.awaitEventsCommittedOnAll("foo", "bar", "oh", "boy");
    }

}
