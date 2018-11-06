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
package io.zeebe.broker.util;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.clustering.base.topology.ReadableTopology;
import io.zeebe.broker.clustering.base.topology.Topology;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyMemberListener;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListener;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.Function;

public class ControlledTopologyManager implements TopologyManager {
  private final Topology topology;
  private Throwable queryError;

  public ControlledTopologyManager() {
    this(
        new NodeInfo(
            0,
            new SocketAddress("0.0.0.0", 0),
            new SocketAddress("0.0.0.0", 1),
            new SocketAddress("0.0.0.0", 2),
            new SocketAddress("0.0.0.0", 3)),
        1,
        1,
        1);
  }

  private ControlledTopologyManager(
      final NodeInfo localNodeInfo, int clusterSize, int partitionsCount, int replicationFactor) {
    this.topology = new Topology(localNodeInfo, clusterSize, partitionsCount, replicationFactor);
  }

  public Topology getTopology() {
    return topology;
  }

  public void setQueryError(Throwable queryError) {
    this.queryError = queryError;
  }

  public void setPartitionLeader(final Partition partition, final NodeInfo leaderInfo) {
    final PartitionInfo partitionInfo = partition.getInfo();
    topology.updatePartition(
        partitionInfo.getPartitionId(),
        partitionInfo.getReplicationFactor(),
        leaderInfo,
        RaftState.LEADER);
  }

  @Override
  public <R> ActorFuture<R> query(Function<ReadableTopology, R> query) {
    if (queryError != null) {
      return CompletableActorFuture.completedExceptionally(queryError);
    } else {
      return CompletableActorFuture.completed(query.apply(topology));
    }
  }

  @Override
  public ActorFuture<TopologyResponseDto> getTopologyDto() {
    return CompletableActorFuture.completed(topology.asDto());
  }

  @Override
  public void removeTopologyMemberListener(TopologyMemberListener listener) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void addTopologyMemberListener(TopologyMemberListener listener) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void removeTopologyPartitionListener(TopologyPartitionListener listener) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void addTopologyPartitionListener(TopologyPartitionListener listener) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
