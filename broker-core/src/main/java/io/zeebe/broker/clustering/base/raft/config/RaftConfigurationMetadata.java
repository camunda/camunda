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
package io.zeebe.broker.clustering.base.raft.config;

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.transport.SocketAddress;

public class RaftConfigurationMetadata extends UnpackedObject
{
    private static final DirectBuffer EMPTY_STRING = new UnsafeBuffer(0, 0);

    protected StringProperty topicNameProp = new StringProperty("topicName", "");
    protected IntegerProperty partitionIdProp = new IntegerProperty("partitionId", -1);
    protected IntegerProperty replicationFactorProp = new IntegerProperty("replicationFactor", -1);
    protected StringProperty logDirectoryProp = new StringProperty("logDirectory", "");
    protected IntegerProperty termProp = new IntegerProperty("term", 0);
    protected StringProperty votedForHostProp = new StringProperty("votedForHost", "");
    protected IntegerProperty votedForPortProp = new IntegerProperty("votedForPort", 0);

    protected ArrayProperty<RaftConfigurationMetadataMember> membersProp = new ArrayProperty<>(
        "members",
        new RaftConfigurationMetadataMember());

    public RaftConfigurationMetadata()
    {
        declareProperty(partitionIdProp);
        declareProperty(replicationFactorProp);
        declareProperty(topicNameProp);
        declareProperty(logDirectoryProp);
        declareProperty(termProp);
        declareProperty(votedForHostProp);
        declareProperty(votedForPortProp);
        declareProperty(membersProp);
    }

    public DirectBuffer getTopicName()
    {
        return topicNameProp.getValue();
    }

    public void setTopicName(final DirectBuffer topicName)
    {
        ensureGreaterThan("Topic name length", topicName.capacity(), 0);
        topicNameProp.setValue(topicName, 0, topicName.capacity());
    }

    public int getPartitionId()
    {
        return partitionIdProp.getValue();
    }

    public int getReplicationFactor()
    {
        return replicationFactorProp.getValue();
    }

    public void setPartitionId(final int partitionId)
    {
        partitionIdProp.setValue(partitionId);
    }

    public void setReplicationFactor(int replicationFactor)
    {
        replicationFactorProp.setValue(replicationFactor);
    }

    public String getLogDirectory()
    {
        return bufferAsString(logDirectoryProp.getValue());
    }

    public void setLogDirectory(final String logDirectory)
    {
        ensureNotNull("Log directory", logDirectory);
        logDirectoryProp.setValue(logDirectory);
    }

    public int getTerm()
    {
        return termProp.getValue();
    }

    public void setTerm(final int term)
    {
        termProp.setValue(term);
    }

    public void getVotedFor(final SocketAddress votedFor)
    {
        votedFor.reset();

        final DirectBuffer votedForValue = votedForHostProp.getValue();
        final int votedForLength = votedForValue.capacity();

        if (votedForLength > 0)
        {
            votedFor.host(votedForValue, 0, votedForLength);
            votedFor.port(votedForPortProp.getValue());
        }
    }

    public void setVotedFor(final SocketAddress votedFor)
    {
        if (votedFor != null)
        {
            votedForHostProp.setValue(votedFor.getHostBuffer(), 0, votedFor.hostLength());
            votedForPortProp.setValue(votedFor.port());
        }
        else
        {
            votedForHostProp.setValue(EMPTY_STRING, 0, 0);
            votedForPortProp.setValue(-1);
        }
    }

    public List<SocketAddress> getMembers()
    {
        final List<SocketAddress> members = new ArrayList<>();

        final Iterator<RaftConfigurationMetadataMember> iterator = membersProp.iterator();
        while (iterator.hasNext())
        {
            final RaftConfigurationMetadataMember configurationMember = iterator.next();
            final DirectBuffer hostBuffer = configurationMember.getHost();


            final SocketAddress member =
                new SocketAddress()
                    .host(hostBuffer, 0, hostBuffer.capacity())
                    .port(configurationMember.getPort());

            members.add(member);
        }

        return members;
    }

    public void setMembers(final List<SocketAddress> members)
    {
        membersProp.reset();

        for (int i = 0; i < members.size(); i++)
        {
            addMember(members.get(i));
        }
    }

    public void addMember(final SocketAddress member)
    {
        ensureNotNull("Member", member);

        membersProp.add()
            .setHost(member.getHostBuffer(), 0, member.hostLength())
            .setPort(member.port());
    }

    public void removeMember(final SocketAddress member)
    {
        ensureNotNull("Member", member);

        final Iterator<RaftConfigurationMetadataMember> iterator = membersProp.iterator();
        while (iterator.hasNext())
        {
            final RaftConfigurationMetadataMember next = iterator.next();
            if (next.getHost().equals(member.getHostBuffer()) &&
                next.getPort() == member.port())
            {
                iterator.remove();
            }
        }
    }
}
