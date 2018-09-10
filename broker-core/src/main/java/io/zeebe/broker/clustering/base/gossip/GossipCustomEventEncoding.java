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
package io.zeebe.broker.clustering.base.gossip;

import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.agrona.BitUtil.SIZE_OF_INT;

import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManagerImpl;
import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.SocketAddress;
import java.nio.ByteOrder;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class GossipCustomEventEncoding {

  public static int writeNodeInfo(
      NodeInfo memberInfo, MutableDirectBuffer directBuffer, int offset) {
    offset = writeNodeId(memberInfo.getNodeId(), directBuffer, offset);
    offset = writeSocketAddress(memberInfo.getManagementApiAddress(), directBuffer, offset);
    offset = writeSocketAddress(memberInfo.getClientApiAddress(), directBuffer, offset);
    offset = writeSocketAddress(memberInfo.getReplicationApiAddress(), directBuffer, offset);
    offset = writeSocketAddress(memberInfo.getSubscriptionApiAddress(), directBuffer, offset);

    return offset;
  }

  private static int writeNodeId(int nodeId, MutableDirectBuffer directBuffer, int offset) {
    directBuffer.putInt(offset, nodeId, ByteOrder.LITTLE_ENDIAN);
    return offset + SIZE_OF_INT;
  }

  private static int writeSocketAddress(
      SocketAddress apiAddress, MutableDirectBuffer directBuffer, int offset) {
    directBuffer.putInt(offset, apiAddress.hostLength(), ByteOrder.LITTLE_ENDIAN);
    offset += SIZE_OF_INT;

    directBuffer.putBytes(offset, apiAddress.getHostBuffer(), 0, apiAddress.hostLength());
    offset += apiAddress.hostLength();

    directBuffer.putInt(offset, apiAddress.port(), ByteOrder.LITTLE_ENDIAN);
    offset += SIZE_OF_INT;

    return offset;
  }

  public static NodeInfo readNodeInfo(int offset, DirectBuffer directBuffer) {
    final int nodeId = directBuffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
    offset += SIZE_OF_INT;

    final SocketAddress managementApi = new SocketAddress();
    offset = readSocketAddress(offset, directBuffer, managementApi);

    final SocketAddress clientApi = new SocketAddress();
    offset = readSocketAddress(offset, directBuffer, clientApi);

    final SocketAddress replicationApi = new SocketAddress();
    offset = readSocketAddress(offset, directBuffer, replicationApi);

    final SocketAddress subscriptionApi = new SocketAddress();
    readSocketAddress(offset, directBuffer, subscriptionApi);

    return new NodeInfo(nodeId, clientApi, managementApi, replicationApi, subscriptionApi);
  }

  private static int readSocketAddress(
      int offset, DirectBuffer directBuffer, SocketAddress apiAddress) {
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

  public static int writePartitions(NodeInfo member, MutableDirectBuffer writeBuffer, int offset) {
    final Set<PartitionInfo> leader = member.getLeaders();
    final Set<PartitionInfo> follower = member.getFollowers();

    final int partitionCount = leader.size() + follower.size();

    writeBuffer.putInt(offset, partitionCount, ByteOrder.LITTLE_ENDIAN);
    offset += SIZE_OF_INT;

    for (PartitionInfo partition : leader) {
      offset = writePartition(partition, RaftState.LEADER, writeBuffer, offset);
    }
    for (PartitionInfo partition : follower) {
      offset = writePartition(partition, RaftState.FOLLOWER, writeBuffer, offset);
    }

    return offset;
  }

  public static int writePartition(
      PartitionInfo partition, RaftState state, MutableDirectBuffer writeBuffer, int offset) {
    writeBuffer.putInt(offset, partition.getPartitionId(), ByteOrder.LITTLE_ENDIAN);
    offset += SIZE_OF_INT;

    writeBuffer.putInt(offset, partition.getReplicationFactor(), ByteOrder.LITTLE_ENDIAN);
    offset += SIZE_OF_INT;

    writeBuffer.putByte(offset, (byte) (state == RaftState.LEADER ? 1 : 0));
    offset += SIZE_OF_BYTE;

    return offset;
  }

  public static void readPartitions(
      DirectBuffer buffer, int offset, NodeInfo member, TopologyManagerImpl topologyManager) {
    final int count = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
    offset += SIZE_OF_INT;

    for (int i = 0; i < count; i++) {
      final int partition = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
      offset += SIZE_OF_INT;

      final int replicationFactor = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
      offset += SIZE_OF_INT;

      final byte stateByte = buffer.getByte(offset);
      offset += SIZE_OF_BYTE;
      final RaftState raftState = stateByte == (byte) 1 ? RaftState.LEADER : RaftState.FOLLOWER;

      topologyManager.updatePartition(partition, replicationFactor, member, raftState);
    }
  }
}
