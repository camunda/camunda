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

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.zeebe.gateway.Loggers;
import io.zeebe.protocol.impl.data.cluster.BrokerInfo;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.slf4j.Logger;

public class BrokerTopologyManagerImpl extends Actor
    implements BrokerTopologyManager, ClusterMembershipEventListener {

  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  protected final BiConsumer<Integer, SocketAddress> registerEndpoint;
  protected final AtomicReference<BrokerClusterStateImpl> topology;

  public BrokerTopologyManagerImpl(final BiConsumer<Integer, SocketAddress> registerEndpoint) {
    this.registerEndpoint = registerEndpoint;
    this.topology = new AtomicReference<>(null);
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  /** @return the current known cluster state or null if the topology was not fetched yet */
  @Override
  public BrokerClusterState getTopology() {
    return topology.get();
  }

  public void setTopology(BrokerClusterStateImpl topology) {
    this.topology.set(topology);
  }

  @Override
  public void event(ClusterMembershipEvent event) {
    final Member subject = event.subject();
    final Type eventType = event.type();
    final BrokerInfo brokerInfo = BrokerInfo.fromProperties(subject.properties());
    LOG.debug("Got membership event {}", brokerInfo);

    if (brokerInfo != null) {
      actor.call(
          () -> {
            Loggers.GATEWAY_LOGGER.debug("Received membership event: {}", event);
            final BrokerClusterStateImpl newTopology = new BrokerClusterStateImpl(topology.get());

            switch (eventType) {
              case MEMBER_ADDED:
                newTopology.addBrokerIfAbsent(brokerInfo.getNodeId());
                processProperties(brokerInfo, newTopology);
                break;

              case METADATA_CHANGED:
                processProperties(brokerInfo, newTopology);
                break;

              case MEMBER_REMOVED:
                newTopology.removeBroker(brokerInfo.getNodeId());
                break;
            }

            topology.set(newTopology);
          });
    }
  }

  // Update topology information based on the distributed event
  private void processProperties(
      BrokerInfo distributedBrokerInfo, BrokerClusterStateImpl newTopology) {

    newTopology.setClusterSize(distributedBrokerInfo.getClusterSize());
    newTopology.setPartitionsCount(distributedBrokerInfo.getPartitionsCount());
    newTopology.setReplicationFactor(distributedBrokerInfo.getReplicationFactor());

    final int nodeId = distributedBrokerInfo.getNodeId();

    distributedBrokerInfo.consumePartitions(
        newTopology::addPartitionIfAbsent,
        leaderPartitionId -> newTopology.setPartitionLeader(leaderPartitionId, nodeId),
        followerPartitionId -> newTopology.addPartitionFollower(followerPartitionId, nodeId));

    final String clientAddress =
        distributedBrokerInfo.getApiAddress(BrokerInfo.CLIENT_API_PROPERTY);
    if (clientAddress != null) {
      newTopology.setBrokerAddressIfPresent(nodeId, clientAddress);
      registerEndpoint.accept(nodeId, SocketAddress.from(clientAddress));
    }
  }
}
