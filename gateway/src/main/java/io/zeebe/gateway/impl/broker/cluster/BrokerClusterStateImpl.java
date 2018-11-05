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
package io.zeebe.gateway.impl.broker.cluster;

import static io.zeebe.transport.ClientTransport.UNKNOWN_NODE_ID;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.transport.SocketAddress;
import java.util.Random;
import java.util.function.BiConsumer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.IntArrayList;

/**
 * Immutable; Important because we hand this between actors. If this is supposed to become mutable,
 * make sure to make copies in the right places.
 */
public class BrokerClusterStateImpl implements BrokerClusterState {

  private final Int2IntHashMap partitionLeaders = new Int2IntHashMap(NODE_ID_NULL);
  private final IntArrayList brokers = new IntArrayList(5, NODE_ID_NULL);
  private final IntArrayList partitions = new IntArrayList(32, PARTITION_ID_NULL);
  private final int clusterSize;
  private final int partitionsCount;
  private final int replicationFactor;

  private final Random randomBroker = new Random();

  public BrokerClusterStateImpl(
      final TopologyResponseDto topologyDto,
      final BiConsumer<Integer, SocketAddress> endpointRegistry) {
    clusterSize = topologyDto.getClusterSize();
    partitionsCount = topologyDto.getPartitionsCount();
    replicationFactor = topologyDto.getReplicationFactor();

    topologyDto
        .brokers()
        .forEach(
            b -> {
              final int nodeId = b.getNodeId();
              endpointRegistry.accept(
                  nodeId, new SocketAddress(bufferAsString(b.getHost()), b.getPort()));
              brokers.add(nodeId);

              b.partitionStates()
                  .forEach(
                      p -> {
                        final int partitionId = p.getPartitionId();
                        partitions.add(partitionId);
                        if (p.isLeader()) {
                          partitionLeaders.put(partitionId, nodeId);
                        }
                      });
            });
  }

  public int getClusterSize() {
    return clusterSize;
  }

  public int getPartitionsCount() {
    return partitionsCount;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  @Override
  public int getLeaderForPartition(final int partition) {
    return partitionLeaders.get(partition);
  }

  @Override
  public int getRandomBroker() {
    if (!brokers.isEmpty()) {
      final int nextBroker = randomBroker.nextInt(brokers.size());
      return brokers.getInt(nextBroker);
    } else {
      return UNKNOWN_NODE_ID;
    }
  }

  @Override
  public IntArrayList getPartitions() {
    return partitions;
  }

  @Override
  public int getPartition(final int offset) {
    if (!partitions.isEmpty()) {
      return partitions.getInt(offset % partitions.size());
    } else {
      return PARTITION_ID_NULL;
    }
  }

  @Override
  public String toString() {
    return "BrokerClusterStateImpl{"
        + "partitionLeaders="
        + partitionLeaders
        + ", brokers="
        + brokers
        + ", partitions="
        + partitions
        + ", clusterSize="
        + clusterSize
        + ", partitionsCount="
        + partitionsCount
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
