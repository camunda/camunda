/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.management.memberList;

import io.zeebe.raft.state.RaftState;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 *
 */
public class RaftStateComposite
{
    private final int partition;
    private final DirectBuffer topicName;
    private RaftState raftState;

    public RaftStateComposite(int partition, DirectBuffer topicName, RaftState raftState)
    {
        this.partition = partition;
        this.topicName = BufferUtil.cloneBuffer(topicName);
        this.raftState = raftState;
    }

    public int getPartition()
    {
        return partition;
    }

    public DirectBuffer getTopicName()
    {
        return topicName;
    }

    public RaftState getRaftState()
    {
        return raftState;
    }

    public void setRaftState(RaftState raftState)
    {
        this.raftState = raftState;
    }

    @Override
    public String toString()
    {
        return "RaftStateComposite{" + "partition=" + partition + ", raftState=" + raftState + '}';
    }
}
