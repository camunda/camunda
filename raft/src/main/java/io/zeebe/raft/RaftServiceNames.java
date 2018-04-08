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

import io.zeebe.raft.state.AbstractRaftState;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.SocketAddress;

public class RaftServiceNames
{
    public static ServiceName<Raft> raftServiceName(String raftName)
    {
        return ServiceName.newServiceName(String.format("raft.%s", raftName), Raft.class);
    }

    public static ServiceName<Void> joinServiceName(String raftName)
    {
        return ServiceName.newServiceName(String.format("raft.%s.joinService", raftName), Void.class);
    }

    public static ServiceName<Void> leaderInstallServiceName(String raftName, int term)
    {
        return ServiceName.newServiceName(String.format("raft.leader.%s.%d.install", raftName, term), Void.class);
    }

    public static ServiceName<Void> leaderOpenLogStreamServiceName(String raftName, int term)
    {
        return ServiceName.newServiceName(String.format("raft.leader.%s.%d.openLogStream", raftName, term), Void.class);
    }

    public static ServiceName<Void> leaderInitialEventCommittedServiceName(String raftName, int term)
    {
        return ServiceName.newServiceName(String.format("raft.leader.%s.%d.initialEventCommitted", raftName, term), Void.class);
    }

    public static ServiceName<Void> replicateLogConrollerServiceName(String raftName, int term, SocketAddress follower)
    {
        return ServiceName.newServiceName(String.format("raft.leader.%s.%d.replicate.%s", raftName, term, follower), Void.class);
    }

    public static ServiceName<AbstractRaftState> leaderServiceName(String raftName, int term)
    {
        return ServiceName.newServiceName(String.format("raft.leader.%s.%d", raftName, term), AbstractRaftState.class);
    }

    public static ServiceName<AbstractRaftState> followerServiceName(String raftName, int term)
    {
        return ServiceName.newServiceName(String.format("raft.follower.%s.%d", raftName, term), AbstractRaftState.class);
    }

    public static ServiceName<Void> pollServiceName(String raftName, int term)
    {
        return ServiceName.newServiceName(String.format("raft.follower.%s.%d.pollService", raftName, term), Void.class);
    }

    public static ServiceName<AbstractRaftState> candidateServiceName(String raftName, int term)
    {
        return ServiceName.newServiceName(String.format("raft.candidate.%s.%d", raftName, term), AbstractRaftState.class);
    }
}
