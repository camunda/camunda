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
package io.zeebe.broker.clustering.management;

import java.util.Iterator;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.management.memberList.MemberListService;
import io.zeebe.broker.clustering.management.memberList.MemberRaftComposite;
import io.zeebe.broker.clustering.management.message.CreatePartitionMessage;
import io.zeebe.broker.clustering.member.Member;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.TransportMessage;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.IntIterator;
import org.agrona.DirectBuffer;

public class PartitionManagerImpl implements PartitionManager
{

    private final MemberListService memberListService;
    private final CreatePartitionMessage messageWriter = new CreatePartitionMessage();
    protected final TransportMessage message = new TransportMessage();
    protected final ClientTransport transport;

    protected final MemberIterator memberIterator = new MemberIterator();

    public PartitionManagerImpl(MemberListService memberListService, ClientTransport transport)
    {
        this.memberListService = memberListService;
        this.transport = transport;
    }

    @Override
    public boolean createPartitionRemote(SocketAddress remote, DirectBuffer topicName, int partitionId)
    {
        final DirectBuffer nameBuffer = BufferUtil.cloneBuffer(topicName);

        messageWriter
            .partitionId(partitionId)
            .topicName(nameBuffer);

        final RemoteAddress remoteAddress = transport.registerRemoteAddress(remote);

        message.writer(messageWriter)
            .remoteAddress(remoteAddress);

        Loggers.SYSTEM_LOGGER.info("Creating partition {}/{} at {}", BufferUtil.bufferAsString(topicName), partitionId, remote);

        return transport.getOutput().sendMessage(message);
    }

    /*
     * There are some issues with how this connects the gossip state with the system partition processing.
     *
     * * not garbage-free
     * * not thread-safe (peer list is shared state between multiple actors and therefore threads)
     * * not efficient (the stream processor iterates all partitions when it looks for a specific
     *   partition's leader)
     *
     * This code can be refactored in any way when we rewrite gossip.
     * As a baseline, the system stream processor needs to know for a set of partitions
     * if a partition leader becomes known. In that case, it must generate a command on the system log.
     */
    @Override
    public Iterator<Member> getKnownMembers()
    {
        memberIterator.wrap(memberListService);
        return memberIterator;
    }

    protected static class MemberIterator implements Iterator<Member>
    {
        protected Iterator<MemberRaftComposite> memberListIterator;
        protected MemberImpl currentMember = new MemberImpl();

        public void wrap(MemberListService memberListService)
        {
            this.memberListIterator = memberListService.iterator();
        }

        @Override
        public boolean hasNext()
        {
            return memberListIterator.hasNext();
        }

        @Override
        public Member next()
        {
            currentMember.wrap(memberListIterator.next());
            return currentMember;
        }
    }

    protected static class MemberImpl implements Member
    {
        protected MemberRaftComposite memberRaftComposite;

        public void wrap(MemberRaftComposite memberRaftComposite)
        {
            this.memberRaftComposite = memberRaftComposite;
        }

        @Override
        public SocketAddress getManagementAddress()
        {
            return memberRaftComposite.getManagementApi();
        }

        @Override
        public IntIterator getLeadingPartitions()
        {
            return memberRaftComposite.getLeadingPartitions();
        }
    }

}
