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

import io.zeebe.raft.state.RaftState;
import io.zeebe.raft.util.RaftClusterRule;
import io.zeebe.raft.util.RaftRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RaftFiveNodesTest
{

    public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();

    public RaftRule raft1 = new RaftRule(actorScheduler, "localhost", 8001, "default", 0);
    public RaftRule raft2 = new RaftRule(actorScheduler, "localhost", 8002, "default", 0, raft1);
    public RaftRule raft3 = new RaftRule(actorScheduler, "localhost", 8003, "default", 0, raft2);
    public RaftRule raft4 = new RaftRule(actorScheduler, "localhost", 8004, "default", 0, raft2, raft3);
    public RaftRule raft5 = new RaftRule(actorScheduler, "localhost", 8005, "default", 0, raft3);

    @Rule
    public RaftClusterRule cluster = new RaftClusterRule(actorScheduler, raft1, raft2, raft3, raft4, raft5);


    @Test
    public void shouldJoinCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();

        // then
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());

        final List<RaftState> raftStateChanges = leader.getRaftStateChanges();
        assertThat(raftStateChanges).containsExactly(RaftState.FOLLOWER, RaftState.CANDIDATE, RaftState.LEADER);
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
        final Raft otherRaft = otherRafts[0].getRaft();
        otherRaft.leave().join();

        // then
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());

        assertThat(leader.getRaft().getMemberSize()).isEqualTo(cluster.getRafts().size() - 2);
        assertThat(otherRaft.getMemberSize()).isEqualTo(cluster.getRafts().size() - 1);
        assertThat(otherRaft.isJoined()).isFalse();
    }

    @Test
    public void shouldReplicateAfterNodeLeavesCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm(), raft1, raft2, raft3, raft4, raft5);
        final RaftRule[] otherRafts = cluster.getOtherRafts(leader);
        final Raft otherRaft = otherRafts[0].getRaft();
        otherRaft.leave().join();
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());

        // when
        final long position = leader.writeEvents("foo", "bar", "end");

        // then
        cluster.getRafts().remove(otherRafts[0]);
        cluster.awaitEventCommittedOnAll(position, leader.getTerm(), "end");
        cluster.awaitEventsCommittedOnAll("foo", "bar", "end");
    }

    @Test
    public void shouldAdjustQuourumOnLeavingCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm(), raft1, raft2, raft3, raft4, raft5);

        // when
        final RaftRule[] otherRafts = cluster.getOtherRafts(leader);

        final int quorum = leader.getRaft().requiredQuorum();
        for (int i = 0; i < quorum; i++)
        {
            final Raft otherRaft = otherRafts[i].getRaft();
            otherRaft.leave().join();
        }

        // then we have a two node cluster
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm());
        assertThat(leader.getRaft().getMemberSize()).isEqualTo(1);
        assertThat(leader.getRaft().requiredQuorum()).isEqualTo(2);

        assertThat(otherRafts[3].getRaft().getMemberSize()).isEqualTo(1);
        assertThat(otherRafts[3].getRaft().requiredQuorum()).isEqualTo(2);
    }

    @Test
    public void shouldReplicateAfterOldQuorumLeavesClusterClean()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
        cluster.awaitRaftEventCommittedOnAll(leader.getTerm(), raft1, raft2, raft3, raft4, raft5);

        final RaftRule[] otherRafts = cluster.getOtherRafts(leader);
        final int quorum = leader.getRaft().requiredQuorum();
        for (int i = 0; i < quorum; i++)
        {
            final Raft otherRaft = otherRafts[i].getRaft();
            otherRaft.leave().join();
            cluster.getRafts().remove(otherRafts[i]);
        }

        // when
        final long position = leader.writeEvents("foo", "bar", "end");

        // then
        cluster.awaitEventCommittedOnAll(position, leader.getTerm(), "end");
        cluster.awaitEventsCommittedOnAll("foo", "bar", "end");
    }

    @Test
    public void shouldReplicateLogEvents()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();
        cluster.awaitLogControllerOpen(leader);

        // when
        final long position = leader.writeEvents("foo", "bar", "end");

        // then
        cluster.awaitEventCommittedOnAll(position, leader.getTerm(), "end");
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
        cluster.awaitLogControllerOpen(newLeader);
        final long position = newLeader.writeEvents("foo", "bar", "end");

        // then
        cluster.awaitEventCommittedOnAll(position, newLeader.getTerm(), "end");
        cluster.awaitEventsCommittedOnAll("foo", "bar", "end");
    }

    @Test
    public void shouldRejoinCluster()
    {
        // given
        final RaftRule oldLeader = cluster.awaitLeader();
        cluster.awaitLogControllerOpen(oldLeader);

        long position = oldLeader.writeEvents("foo", "bar");
        cluster.awaitEventCommittedOnAll(position, oldLeader.getTerm(), "bar");

        // when leader leaves the cluster
        cluster.removeRaft(oldLeader);

        // and a new leader writes more events
        final RaftRule newLeader = cluster.awaitLeader();
        cluster.awaitLogControllerOpen(newLeader);

        position = newLeader.writeEvents("hello", "world");
        cluster.awaitEventCommittedOnAll(position, newLeader.getTerm(), "world");

        // and the old leader rejoins the cluster
        cluster.registerRaft(oldLeader);

        // then the new events are also committed on the old leader
        cluster.awaitEventCommitted(oldLeader, position, newLeader.getTerm(), "world");
        cluster.awaitEventsCommittedOnAll("foo", "bar", "hello", "world");
    }

    @Test
    public void shouldTruncateLog()
    {
        // given a log with two events committed
        final RaftRule oldLeader = cluster.awaitLeader();
        cluster.awaitLogControllerOpen(oldLeader);
        cluster.awaitRaftEventCommittedOnAll(oldLeader.getTerm());

        long position = oldLeader.writeEvents("foo", "bar");
        cluster.awaitEventCommittedOnAll(position, oldLeader.getTerm(), "bar");
        cluster.awaitRaftEventCommittedOnAll(oldLeader.getTerm());

        // when a quorum leaves the cluster
        final RaftRule[] otherRafts = cluster.getOtherRafts(oldLeader);
        cluster.removeRafts(otherRafts);

        // and more events are written
        position = oldLeader.writeEvents("hello", "world");
        cluster.awaitEventAppendedOnAll(position, oldLeader.getTerm(), "world");

        // and leader and remaining leaves cluster
        cluster.removeRafts(oldLeader);

        // and quorum returns
        cluster.registerRafts(otherRafts);

        // and a new leader writes more events
        final RaftRule newLeader = cluster.awaitLeader();
        cluster.awaitLogControllerOpen(newLeader);

        position = newLeader.writeEvents("oh", "boy");
        cluster.awaitEventCommittedOnAll(position, newLeader.getTerm(), "boy");

        // and the nodes with the extended older log rejoins the cluster
        cluster.registerRaft(oldLeader);

        // then the new events are also committed on the returning nodes discarding there uncommitted events
        cluster.awaitInitialEventCommittedOnAll(newLeader.getTerm());
        cluster.awaitEventCommittedOnAll(position, newLeader.getTerm(), "boy");
        cluster.awaitEventsCommittedOnAll("foo", "bar", "oh", "boy");
    }

}
