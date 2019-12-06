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
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;

public class BrokerTopologyManagerImpl extends Actor
    implements BrokerTopologyManager, ClusterMembershipEventListener {

  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  protected final BiConsumer<Integer, SocketAddress> registerEndpoint;
  protected final AtomicReference<BrokerClusterStateImpl> topology;
  private final Supplier<Set<Member>> membersSupplier;

  public BrokerTopologyManagerImpl(
      Supplier<Set<Member>> membersSupplier,
      final BiConsumer<Integer, SocketAddress> registerEndpoint) {
    this.membersSupplier = membersSupplier;
    this.registerEndpoint = registerEndpoint;
    this.topology = new AtomicReference<>(null);
  }

  /** @return the current known cluster state or null if the topology was not fetched yet */
  @Override
  public BrokerClusterState getTopology() {
    return topology.get();
  }

  private void checkForMissingEvents() {
    final Set<Member> members = membersSupplier.get();
    if (members == null || members.isEmpty()) {
      return;
    }

    final BrokerClusterStateImpl newTopology = new BrokerClusterStateImpl(topology.get());
    for (Member member : members) {
      final BrokerInfo brokerInfo = BrokerInfo.fromProperties(member.properties());
      if (brokerInfo != null) {
        newTopology.addBrokerIfAbsent(brokerInfo.getNodeId());
        processProperties(brokerInfo, newTopology);
      }
    }
    topology.set(newTopology);
  }

  @Override
  protected void onActorStarted() {
    // to make gateway topology more robust we need to check for missing events periodically
    actor.runAtFixedRate(Duration.ofSeconds(5), this::checkForMissingEvents);
  }

  public void setTopology(BrokerClusterStateImpl topology) {
    this.topology.set(topology);
  }

  @Override
  public String getName() {
    return "GatewayTopologyManager";
  }

  @Override
  public void event(ClusterMembershipEvent event) {
    final Member subject = event.subject();
    final Type eventType = event.type();
    final BrokerInfo brokerInfo = BrokerInfo.fromProperties(subject.properties());

    if (brokerInfo != null) {
      actor.call(
          () -> {
            final BrokerClusterStateImpl newTopology = new BrokerClusterStateImpl(topology.get());

            switch (eventType) {
              case MEMBER_ADDED:
                LOG.debug("Received new broker {}.", brokerInfo);
                newTopology.addBrokerIfAbsent(brokerInfo.getNodeId());
                processProperties(brokerInfo, newTopology);
                break;

              case METADATA_CHANGED:
                LOG.debug(
                    "Received metadata change from Broker {}, partitions {} and terms {}.",
                    brokerInfo.getNodeId(),
                    brokerInfo.getPartitionRoles(),
                    brokerInfo.getPartitionLeaderTerms());
                newTopology.addBrokerIfAbsent(brokerInfo.getNodeId());
                processProperties(brokerInfo, newTopology);
                break;

              case MEMBER_REMOVED:
                LOG.debug("Received broker was removed {}.", brokerInfo);
                newTopology.removeBroker(brokerInfo.getNodeId());
                break;

              case REACHABILITY_CHANGED:
              default:
                LOG.debug(
                    "Received {} for broker {}, do nothing.", eventType, brokerInfo.getNodeId());
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
        (leaderPartitionId, term) ->
            newTopology.setPartitionLeader(leaderPartitionId, nodeId, term),
        followerPartitionId -> newTopology.addPartitionFollower(followerPartitionId, nodeId));

    final String clientAddress = distributedBrokerInfo.getCommandApiAddress();
    if (clientAddress != null) {
      newTopology.setBrokerAddressIfPresent(nodeId, clientAddress);
      registerEndpoint.accept(nodeId, SocketAddress.from(clientAddress));
    }
  }
}
