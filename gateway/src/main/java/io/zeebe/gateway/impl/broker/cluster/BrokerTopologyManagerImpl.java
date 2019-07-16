/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.cluster;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.zeebe.gateway.Loggers;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
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

    if (brokerInfo != null) {
      actor.call(
          () -> {
            Loggers.GATEWAY_LOGGER.debug(
                "Received membership event: {} with {} ", event, brokerInfo);
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

    final String clientAddress = distributedBrokerInfo.getCommandApiAddress();
    if (clientAddress != null) {
      newTopology.setBrokerAddressIfPresent(nodeId, clientAddress);
      registerEndpoint.accept(nodeId, SocketAddress.from(clientAddress));
    }
  }
}
