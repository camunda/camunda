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
package io.zeebe.broker.clustering.base.topology;

import java.util.*;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.topology.TopologyDto.BrokerDto;
import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

/**
 * Represents this node's view of the cluster. Includes
 * info about known nodes as well as partitions and their current distribution to nodes.
 */
public class Topology
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final NodeInfo local;

    private final Int2ObjectHashMap<PartitionInfo> partitions = new Int2ObjectHashMap<>();
    private final List<NodeInfo> members = new ArrayList<>();

    private final Int2ObjectHashMap<NodeInfo> partitionLeaders = new Int2ObjectHashMap<>();
    private final Int2ObjectHashMap<List<NodeInfo>> partitionFollowers = new Int2ObjectHashMap<>();

    public Topology(NodeInfo localBroker)
    {
        this.local = localBroker;
        this.addMember(localBroker);
    }

    public NodeInfo getLocal()
    {
        return local;
    }

    public NodeInfo getMemberByAddress(SocketAddress apiAddress)
    {
        NodeInfo member = null;

        for (int i = 0; i < members.size() && member == null; i++)
        {
            final NodeInfo current = members.get(i);

            if (current.getApiPort().equals(apiAddress))
            {
                member = current;
            }
        }

        return member;
    }

    public List<NodeInfo> getMembers()
    {
        return members;
    }

    public PartitionInfo getParition(int partitionId)
    {
        return partitions.get(partitionId);
    }

    public NodeInfo getLeader(int partitionId)
    {
        return partitionLeaders.get(partitionId);
    }

    public List<NodeInfo> getFollowers(int partitionId)
    {
        return partitionFollowers.get(partitionId);
    }

    public Collection<PartitionInfo> getPartitions()
    {
        return new ArrayList<>(partitions.values());
    }

    public void addMember(NodeInfo member)
    {
        // replace member if present
        if (!members.contains(member))
        {
            LOG.debug("Adding {} to list of known members", member);

            members.add(member);
        }
    }

    public void removeMember(NodeInfo member)
    {
        LOG.debug("Removing {} from list of known members", member);

        for (PartitionInfo partition : member.follower)
        {
            final List<NodeInfo> followers = partitionFollowers.get(partition.getPartitionId());

            if (followers != null)
            {
                followers.remove(member);
            }
        }

        for (PartitionInfo partition : member.leader)
        {
            partitionLeaders.remove(partition.getPartitionId());
        }

        members.remove(member);
    }

    public void removePartitionForMember(int partitionId, NodeInfo memberInfo)
    {
        final PartitionInfo partition = partitions.get(partitionId);
        if (partition == null)
        {
            return;
        }

        LOG.debug("Removing {} list of known partitions", partition);

        memberInfo.leader.remove(partition);
        memberInfo.follower.remove(partition);

        final List<NodeInfo> followers = partitionFollowers.get(partitionId);
        if (followers != null)
        {
            followers.remove(memberInfo);
        }

        final NodeInfo member = partitionLeaders.get(partitionId);
        if (member != null && member.equals(memberInfo))
        {
            partitionLeaders.remove(partitionId);
        }
    }

    public PartitionInfo updatePartition(int paritionId, DirectBuffer topicName, int replicationFactor, NodeInfo member, RaftState state)
    {
        List<NodeInfo> followers = partitionFollowers.get(paritionId);

        PartitionInfo partition = partitions.get(paritionId);
        if (partition == null)
        {
            partition = new PartitionInfo(topicName, paritionId, replicationFactor);
            partitions.put(paritionId, partition);
        }

        LOG.debug("Updating partition information for parition {}", partition);

        switch (state)
        {
            case LEADER:
                if (followers != null)
                {
                    followers.remove(member);
                }
                partitionLeaders.put(paritionId, member);

                member.follower.remove(partition);

                if (!member.leader.contains(partition))
                {
                    member.leader.add(partition);
                }
                break;

            case FOLLOWER:
                if (member.equals(partitionLeaders.get(paritionId)))
                {
                    partitionLeaders.remove(paritionId);
                }
                if (followers == null)
                {
                    followers = new ArrayList<>();
                    partitionFollowers.put(paritionId, followers);
                }
                if (!followers.contains(member))
                {
                    followers.add(member);
                }

                member.leader.remove(partition);

                if (!member.follower.contains(partition))
                {
                    member.follower.add(partition);
                }
                break;

            case CANDIDATE:
                // internal raft state: not tracked by topology
                break;
        }

        return partition;
    }

    public TopologyDto asDto()
    {
        final TopologyDto dto = new TopologyDto();

        for (NodeInfo member : members)
        {
            final BrokerDto broker = dto.brokers().add();
            final SocketAddress apiContactPoint = member.getApiPort();
            broker.setHost(apiContactPoint.getHostBuffer(), 0, apiContactPoint.getHostBuffer().capacity());
            broker.setPort(apiContactPoint.port());

            for (PartitionInfo partition : member.getLeader())
            {
                final DirectBuffer topicName = BufferUtil.cloneBuffer(partition.getTopicName());

                broker.partitionStates()
                    .add()
                    .setPartitionId(partition.getPartitionId())
                    .setTopicName(topicName, 0, topicName.capacity())
                    .setReplicationFactor(partition.getReplicationFactor())
                    .setState(RaftState.LEADER);
            }

            for (PartitionInfo partition : member.getFollower())
            {
                final DirectBuffer topicName = BufferUtil.cloneBuffer(partition.getTopicName());

                broker.partitionStates()
                    .add()
                    .setPartitionId(partition.getPartitionId())
                    .setTopicName(topicName, 0, topicName.capacity())
                    .setReplicationFactor(partition.getReplicationFactor())
                    .setState(RaftState.LEADER);
            }
        }

        return dto;
    }

    public static class NodeInfo
    {
        private final SocketAddress apiPort;
        private final SocketAddress managementPort;
        private final SocketAddress replicationPort;

        private final List<PartitionInfo> leader = new ArrayList<>();
        private final List<PartitionInfo> follower = new ArrayList<>();

        public NodeInfo(SocketAddress apiPort,
            SocketAddress managementPort,
            SocketAddress replicationPort)
        {
            this.apiPort = apiPort;
            this.managementPort = managementPort;
            this.replicationPort = replicationPort;
        }

        public SocketAddress getApiPort()
        {
            return apiPort;
        }

        public SocketAddress getManagementPort()
        {
            return managementPort;
        }

        public SocketAddress getReplicationPort()
        {
            return replicationPort;
        }

        public List<PartitionInfo> getLeader()
        {
            return leader;
        }

        public List<PartitionInfo> getFollower()
        {
            return follower;
        }

        @Override
        public String toString()
        {
            return String.format("Node{clientApi=%s, managementApi=%s, replicationApi=%s}", apiPort, managementPort, replicationPort);
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((apiPort == null) ? 0 : apiPort.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            final NodeInfo other = (NodeInfo) obj;
            if (apiPort == null)
            {
                if (other.apiPort != null)
                {
                    return false;
                }
            }
            else if (!apiPort.equals(other.apiPort))
            {
                return false;
            }
            return true;
        }
    }

    public static class PartitionInfo
    {
        private final DirectBuffer topicName;
        private final int paritionId;
        private final int replicationFactor;

        public PartitionInfo(DirectBuffer topicName, int paritionId, int replicationFactor)
        {
            this.topicName = topicName;
            this.paritionId = paritionId;
            this.replicationFactor = replicationFactor;
        }

        public DirectBuffer getTopicName()
        {
            return topicName;
        }

        public String getTopicNameAsString()
        {
            return BufferUtil.bufferAsString(topicName);
        }

        public int getPartitionId()
        {
            return paritionId;
        }

        public int getReplicationFactor()
        {
            return replicationFactor;
        }

        @Override
        public String toString()
        {
            return String.format("Partition{topic=%s, partitionId=%d, replicationFactor=%d}", getTopicNameAsString(), paritionId, replicationFactor);
        }
    }
}
