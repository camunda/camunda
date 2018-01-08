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

import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.agrona.BitUtil.SIZE_OF_INT;

import java.nio.ByteOrder;
import java.util.List;

import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.SocketAddress;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 *
 */
public final class GossipEventCreationHelper
{
    public static DirectBuffer writeAPIAddressesIntoBuffer(SocketAddress managementApi,
                                                           SocketAddress replicationApi,
                                                           SocketAddress clientApi,
                                                           MutableDirectBuffer directBuffer)
    {
        int offset = 0;
        offset = writeApiAddressIntoBuffer(offset, managementApi, directBuffer);
        offset = writeApiAddressIntoBuffer(offset, clientApi, directBuffer);
        writeApiAddressIntoBuffer(offset, replicationApi, directBuffer);

        return directBuffer;
    }

    private static int writeApiAddressIntoBuffer(int offset, SocketAddress apiAddress, MutableDirectBuffer directBuffer)
    {
        directBuffer.putInt(offset, apiAddress.hostLength(), ByteOrder.LITTLE_ENDIAN);
        offset += SIZE_OF_INT;

        directBuffer.putBytes(offset, apiAddress.getHostBuffer(), 0, apiAddress.hostLength());
        offset += apiAddress.hostLength();

        directBuffer.putInt(offset, apiAddress.port(), ByteOrder.LITTLE_ENDIAN);
        offset += SIZE_OF_INT;
        return offset;
    }

    public static int readFromBufferIntoSocketAddress(int offset, DirectBuffer directBuffer, SocketAddress apiAddress)
    {
        final int hostLength = directBuffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
        offset += SIZE_OF_INT;

        final byte[] host = new byte[hostLength];
        directBuffer.getBytes(offset, host);
        offset += hostLength;

        final int port = directBuffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
        offset += SIZE_OF_INT;

        apiAddress.host(host, 0, hostLength);
        apiAddress.port(port);

        return offset;
    }

    public static DirectBuffer writeRaftsIntoBuffer(List<RaftStateComposite> rafts, MutableDirectBuffer directBuffer)
    {
        final int raftCount = rafts.size();

        int offset = 0;
        directBuffer.putInt(offset, raftCount, ByteOrder.LITTLE_ENDIAN);
        offset += SIZE_OF_INT;

        for (int i = 0; i < raftCount; i++)
        {
            final RaftStateComposite raft = rafts.get(i);

            directBuffer.putInt(offset, raft.getPartition(), ByteOrder.LITTLE_ENDIAN);
            offset += SIZE_OF_INT;

            final DirectBuffer currentTopicName = raft.getTopicName();
            directBuffer.putInt(offset, currentTopicName.capacity(), ByteOrder.LITTLE_ENDIAN);
            offset += SIZE_OF_INT;

            directBuffer.putBytes(offset, currentTopicName, 0, currentTopicName.capacity());
            offset += currentTopicName.capacity();

            directBuffer.putByte(offset, raft.getRaftState() == RaftState.LEADER ? (byte) 1 : (byte) 0);
            offset += SIZE_OF_BYTE;
        }

        return directBuffer;
    }

    public static void updateMemberWithNewRaftState(MemberRaftComposite memberRaftComposite, DirectBuffer memberRaftStatesBuffer)
    {
        int offset = 0;
        final int count = memberRaftStatesBuffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
        offset += SIZE_OF_INT;

        for (int i = 0; i < count; i++)
        {
            final int partition = memberRaftStatesBuffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
            offset += SIZE_OF_INT;

            final int topicNameLength = memberRaftStatesBuffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
            offset += SIZE_OF_INT;

            final MutableDirectBuffer topicBuffer = new UnsafeBuffer(new byte[topicNameLength]);
            memberRaftStatesBuffer.getBytes(offset, topicBuffer, 0, topicNameLength);
            offset += topicNameLength;

            final byte state = memberRaftStatesBuffer.getByte(offset);
            offset += SIZE_OF_BYTE;

            memberRaftComposite.updateRaft(partition, topicBuffer, state == (byte) 1 ? RaftState.LEADER : RaftState.FOLLOWER);
        }
    }
}
