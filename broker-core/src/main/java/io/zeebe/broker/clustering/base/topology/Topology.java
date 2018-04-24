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
import java.util.function.Function;

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
public class Topology implements ReadableTopology
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

    @Override
    public NodeInfo getLocal()
    {
        return local;
    }

    @Override
    public NodeInfo getMemberByClientApi(SocketAddress apiAddress)
    {
        return getMemberByApi(NodeInfo::getClientApiAddress, apiAddress);
    }

    @Override
    public NodeInfo getMemberByManagementApi(SocketAddress apiAddress)
    {
        return getMemberByApi(NodeInfo::getManagementApiAddress, apiAddress);
    }

    @Override
    public NodeInfo getMemberByReplicationApi(SocketAddress apiAddress)
    {
        return getMemberByApi(NodeInfo::getReplicationApiAddress, apiAddress);
    }

    protected NodeInfo getMemberByApi(Function<NodeInfo, SocketAddress> apiAddressMapper, SocketAddress apiAddress)
    {
        NodeInfo member = null;

        for (int i = 0; i < members.size() && member == null; i++)
        {
            final NodeInfo current = members.get(i);

            if (apiAddressMapper.apply(current).equals(apiAddress))
            {
                member = current;
            }
        }

        return member;
    }

    @Override
    public List<NodeInfo> getMembers()
    {
        return members;
    }

    @Override
    public PartitionInfo getParition(int partitionId)
    {
        return partitions.get(partitionId);
    }

    @Override
    public NodeInfo getLeader(int partitionId)
    {
        return partitionLeaders.get(partitionId);
    }

    @Override
    public List<NodeInfo> getFollowers(int partitionId)
    {
        return partitionFollowers.getOrDefault(partitionId, Collections.emptyList());
    }

    @Override
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

        for (PartitionInfo partition : member.getFollowers())
        {
            final List<NodeInfo> followers = partitionFollowers.get(partition.getPartitionId());

            if (followers != null)
            {
                followers.remove(member);
            }
        }

        for (PartitionInfo partition : member.getLeaders())
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

        memberInfo.removeLeader(partition);
        memberInfo.removeFollower(partition);

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

        LOG.debug("Updating partition information for partition {} and state {}", partition, state);

        if (state != null)
        {
            switch (state)
            {
                case LEADER:
                    if (followers != null)
                    {
                        followers.remove(member);
                    }
                    partitionLeaders.put(paritionId, member);

                    member.removeFollower(partition);
                    member.addLeader(partition);
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

                    member.removeLeader(partition);
                    member.addFollower(partition);
                    break;

                case CANDIDATE:
                    // internal raft state: not tracked by topology
                    break;
            }
        }

        return partition;
    }

    @Override
    public TopologyDto asDto()
    {
        final TopologyDto dto = new TopologyDto();

        for (NodeInfo member : members)
        {
            final BrokerDto broker = dto.brokers().add();
            final SocketAddress apiContactPoint = member.getClientApiAddress();
            broker.setHost(apiContactPoint.getHostBuffer(), 0, apiContactPoint.getHostBuffer().capacity());
            broker.setPort(apiContactPoint.port());

            for (PartitionInfo partition : member.getLeaders())
            {
                final DirectBuffer topicName = BufferUtil.cloneBuffer(partition.getTopicNameBuffer());

                broker.partitionStates()
                    .add()
                    .setPartitionId(partition.getPartitionId())
                    .setTopicName(topicName, 0, topicName.capacity())
                    .setReplicationFactor(partition.getReplicationFactor())
                    .setState(RaftState.LEADER);
            }

            for (PartitionInfo partition : member.getFollowers())
            {
                final DirectBuffer topicName = BufferUtil.cloneBuffer(partition.getTopicNameBuffer());

                broker.partitionStates()
                    .add()
                    .setPartitionId(partition.getPartitionId())
                    .setTopicName(topicName, 0, topicName.capacity())
                    .setReplicationFactor(partition.getReplicationFactor())
                    .setState(RaftState.FOLLOWER);
            }
        }

        return dto;
    }

}
