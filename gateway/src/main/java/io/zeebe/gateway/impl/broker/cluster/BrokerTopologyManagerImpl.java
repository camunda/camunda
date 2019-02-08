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

import static io.zeebe.gateway.impl.broker.BrokerClientImpl.LOG;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class BrokerTopologyManagerImpl extends Actor
    implements BrokerTopologyManager, ClusterMembershipEventListener {
  protected final ClientOutput output;
  protected final BiConsumer<Integer, SocketAddress> registerEndpoint;
  protected final AtomicReference<BrokerClusterStateImpl> topology;
  private final ObjectMapper objectMapper;
  private String atomixMemberId;

  public BrokerTopologyManagerImpl(
      final ClientOutput output,
      final BiConsumer<Integer, SocketAddress> registerEndpoint,
      String atomixMemberId) {
    this.output = output;
    this.registerEndpoint = registerEndpoint;
    this.atomixMemberId = atomixMemberId;

    this.objectMapper = new ObjectMapper();
    this.topology = new AtomicReference<>(new BrokerClusterStateImpl());
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  /** @return the current known cluster state or null if the topology was not fetched yet */
  @Override
  public BrokerClusterState getTopology() {
    return topology.get();
  }

  @Override
  public void event(ClusterMembershipEvent event) {
    actor.call(
        () -> {
          LOG.debug("{}: Received event: {}", atomixMemberId, event);
          final Member subject = event.subject();
          final Type eventType = event.type();

          final BrokerClusterStateImpl newTopology = new BrokerClusterStateImpl(topology.get());
          final TopologyDistributionInfo remoteTopology = extractTopologyFromProperties(subject);

          if (remoteTopology == null) {
            LOG.debug("{}: Ignoring event from {}", atomixMemberId, subject.id());
            return;
          }

          switch (eventType) {
            case MEMBER_ADDED:
              newTopology.addBrokerIfAbsent(remoteTopology.getNodeId());
              processProperties(remoteTopology, newTopology);
              break;

            case METADATA_CHANGED:
              processProperties(remoteTopology, newTopology);
              break;

            case MEMBER_REMOVED:
              newTopology.removeBroker(remoteTopology.getNodeId());
              processProperties(remoteTopology, newTopology);
              break;
          }

          topology.set(newTopology);
        });
  }

  // Update topology information based on the distributed event
  private void processProperties(
      TopologyDistributionInfo remoteTopology, BrokerClusterStateImpl newTopology) {

    newTopology.setClusterSize(remoteTopology.getClusterSize());
    newTopology.setPartitionsCount(remoteTopology.getPartitionsCount());
    newTopology.setReplicationFactor(remoteTopology.getReplicationFactor());

    for (Integer partitionId : remoteTopology.getPartitionRoles().keySet()) {
      newTopology.addPartitionIfAbsent(partitionId);

      if (remoteTopology.getPartitionNodeRole(partitionId) == RaftState.LEADER) {
        newTopology.setPartitionLeader(partitionId, remoteTopology.getNodeId());
      }
    }

    final String clientAddress = remoteTopology.getApiAddress("client");
    newTopology.setBrokerAddressIfPresent(remoteTopology.getNodeId(), clientAddress);
    registerEndpoint.accept(remoteTopology.getNodeId(), SocketAddress.from(clientAddress));
  }

  // Try to extract a topology from the node's properties
  private TopologyDistributionInfo extractTopologyFromProperties(Member eventSource) {
    final String jsonTopology = eventSource.properties().getProperty("topology");
    if (jsonTopology == null) {
      LOG.debug("Node {} has no topology information", eventSource.id());
      return null;
    }

    final TopologyDistributionInfo distInfo;
    try {
      distInfo = objectMapper.readValue(jsonTopology, TopologyDistributionInfo.class);
    } catch (IOException e) {
      LOG.error("Error reading topology {} of node {}", e.getMessage(), eventSource.id());
      return null;
    }

    return distInfo;
  }
}
