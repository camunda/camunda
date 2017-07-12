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

import io.zeebe.raft.util.ActorSchedulerRule;
import io.zeebe.raft.util.RaftClusterRule;
import io.zeebe.raft.util.RaftRule;
import org.junit.Rule;
import org.junit.Test;

public class RaftSingleNodeTest
{

    public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();

    public RaftRule raft1 = new RaftRule(actorScheduler, "localhost", 8001, "default", 0);

    @Rule
    public RaftClusterRule cluster = new RaftClusterRule(actorScheduler, raft1);


    @Test
    public void shouldJoinCluster()
    {
        // given
        final RaftRule leader = cluster.awaitLeader();

        // then
        cluster.awaitInitialEventCommittedOnAll(leader.getTerm());
    }

    @Test
    public void shouldCommitEntries()
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

}
