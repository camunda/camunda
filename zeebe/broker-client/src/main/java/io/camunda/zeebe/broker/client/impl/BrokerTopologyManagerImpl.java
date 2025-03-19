/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.PartitionRoleValues;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.Actor;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrokerTopologyManagerImpl extends Actor
    implements BrokerTopologyManager,
        ClusterMembershipEventListener,
        ClusterConfigurationUpdateListener {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerTopologyManagerImpl.class);
  private volatile BrokerClusterStateImpl topology = new BrokerClusterStateImpl();
  private volatile ClusterConfiguration clusterConfiguration = ClusterConfiguration.uninitialized();
  private final Supplier<Set<Member>> membersSupplier;
  private final BrokerClientTopologyMetrics topologyMetrics;

  private final Set<BrokerTopologyListener> topologyListeners = new HashSet<>();

  public BrokerTopologyManagerImpl(
      final Supplier<Set<Member>> membersSupplier,
      final BrokerClientTopologyMetrics topologyMetrics) {
    this.membersSupplier = membersSupplier;
    this.topologyMetrics = topologyMetrics;
  }

  /**
   * @return a copy of the currently known topology
   */
  @Override
  public BrokerClusterState getTopology() {
    return topology;
  }

  @Override
  public ClusterConfiguration getClusterConfiguration() {
    return clusterConfiguration;
  }

  @Override
  public void addTopologyListener(final BrokerTopologyListener listener) {
    actor.run(
        () -> {
          topologyListeners.add(listener);
          topology.getBrokers().stream()
              .map(b -> MemberId.from(String.valueOf(b)))
              .forEach(listener::brokerAdded);
        });
  }

  @Override
  public void removeTopologyListener(final BrokerTopologyListener listener) {
    actor.run(() -> topologyListeners.remove(listener));
  }

  private void updateTopology(final Consumer<BrokerClusterStateImpl> updater) {
    actor.run(
        () -> {
          final var updated = new BrokerClusterStateImpl(topology);
          updater.accept(updated);
          topology = updated;
          updateMetrics(updated);
        });
  }

  private void checkForMissingEvents() {
    final Set<Member> members = membersSupplier.get();
    if (members == null || members.isEmpty()) {
      return;
    }
    updateTopology(
        topology -> {
          for (final Member member : members) {
            final BrokerInfo brokerInfo = BrokerInfo.fromProperties(member.properties());
            if (brokerInfo != null) {
              addBroker(topology, member, brokerInfo);
            }
          }
        });
  }

  private void addBroker(
      final BrokerClusterStateImpl topology, final Member member, final BrokerInfo brokerInfo) {
    if (topology.addBrokerIfAbsent(brokerInfo.getNodeId())) {
      topologyListeners.forEach(l -> l.brokerAdded(member.id()));
    }

    processProperties(topology, brokerInfo);
  }

  private void removeBroker(
      final BrokerClusterStateImpl topology, final Member member, final BrokerInfo brokerInfo) {
    topology.removeBroker(brokerInfo.getNodeId());
    topologyListeners.forEach(l -> l.brokerRemoved(member.id()));
  }

  @Override
  public String getName() {
    return "GatewayTopologyManager";
  }

  @Override
  protected void onActorStarted() {
    // Get the initial member state before the listener is registered
    checkForMissingEvents();
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    final Member subject = event.subject();
    final Type eventType = event.type();
    final BrokerInfo brokerInfo = BrokerInfo.fromProperties(subject.properties());

    if (brokerInfo == null) {
      return;
    }

    switch (eventType) {
      case MEMBER_ADDED -> {
        LOG.debug("Received new broker {}.", brokerInfo);
        updateTopology(topology -> addBroker(topology, subject, brokerInfo));
      }
      case METADATA_CHANGED -> {
        LOG.debug(
            "Received metadata change from Broker {}, partitions {}, terms {} and health {}.",
            brokerInfo.getNodeId(),
            brokerInfo.getPartitionRoles(),
            brokerInfo.getPartitionLeaderTerms(),
            brokerInfo.getPartitionHealthStatuses());
        updateTopology(topology -> addBroker(topology, subject, brokerInfo));
      }
      case MEMBER_REMOVED -> {
        LOG.debug("Received broker was removed {}.", brokerInfo);
        updateTopology(topology -> removeBroker(topology, subject, brokerInfo));
      }
      default ->
          LOG.debug("Received {} for broker {}, do nothing.", eventType, brokerInfo.getNodeId());
    }
  }

  // Update topology information based on the distributed event
  private void processProperties(
      final BrokerClusterStateImpl topology, final BrokerInfo distributedBrokerInfo) {

    // Do not overwrite clusterSize received from BrokerInfo. ClusterTopology received via
    // GatewayClusterTopologyService.Listener. BrokerInfo contains the static clusterSize which is
    // the initial clusterSize. However, we still have to initialize it because it should have the
    // correct value even when the dynamic ClusterTopology is disabled.
    if (!topology.isInitialized()) {
      topology.setClusterSize(distributedBrokerInfo.getClusterSize());
      topology.setPartitionsCount(distributedBrokerInfo.getPartitionsCount());
      topology.setReplicationFactor(distributedBrokerInfo.getReplicationFactor());
    }

    final int nodeId = distributedBrokerInfo.getNodeId();

    topology.syncPartitions(nodeId, distributedBrokerInfo.getPartitionRoles().keySet());
    distributedBrokerInfo.consumePartitions(
        topology::addPartitionIfAbsent,
        (leaderPartitionId, term) -> topology.setPartitionLeader(leaderPartitionId, nodeId, term),
        followerPartitionId -> topology.addPartitionFollower(followerPartitionId, nodeId),
        inactivePartitionId -> topology.addPartitionInactive(inactivePartitionId, nodeId));

    distributedBrokerInfo.consumePartitionsHealth(
        (partition, health) -> topology.setPartitionHealthStatus(nodeId, partition, health));

    final String clientAddress = distributedBrokerInfo.getCommandApiAddress();
    if (clientAddress != null) {
      topology.setBrokerAddressIfPresent(nodeId, clientAddress);
    }

    topology.setBrokerVersionIfPresent(nodeId, distributedBrokerInfo.getVersion());
  }

  private void updateMetrics(final BrokerClusterStateImpl topology) {
    final var partitions = topology.getPartitions();
    partitions.forEach(
        partition -> {
          final var leader = topology.getLeaderForPartition(partition);
          if (leader != BrokerClusterState.NODE_ID_NULL) {
            topologyMetrics.setRoleForPartition(partition, leader, PartitionRoleValues.LEADER);
          }

          final var followers = topology.getFollowersForPartition(partition);
          if (followers != null) {
            followers.forEach(
                broker ->
                    topologyMetrics.setRoleForPartition(
                        partition, broker, PartitionRoleValues.FOLLOWER));
          }
        });
  }

  @Override
  public void onClusterConfigurationUpdated(final ClusterConfiguration clusterTopology) {
    if (clusterTopology.isUninitialized()) {
      return;
    }
    clusterConfiguration = clusterTopology;

    updateTopology(
        topologyToUpdate -> {
          notifyOnClusterChangeCompleted(clusterTopology, topologyToUpdate);

          final var newClusterSize = clusterTopology.clusterSize();
          final var newPartitionsCount = clusterTopology.partitionCount();
          final var newReplicationFactor = clusterTopology.minReplicationFactor();

          if (newClusterSize != topologyToUpdate.getClusterSize()
              || newPartitionsCount != topologyToUpdate.getPartitionsCount()
              || newReplicationFactor != topologyToUpdate.getReplicationFactor()) {
            LOG.debug(
                "Updating topology with clusterSize {}, partitionsCount {} and replicationFactor {}",
                newClusterSize,
                newPartitionsCount,
                newReplicationFactor);
            topologyToUpdate.setClusterSize(newClusterSize);
            topologyToUpdate.setPartitionsCount(newPartitionsCount);
            topologyToUpdate.setReplicationFactor(newReplicationFactor);
          }
        });
  }

  private void notifyOnClusterChangeCompleted(
      final ClusterConfiguration clusterTopology, final BrokerClusterStateImpl topologyToUpdate) {

    clusterTopology
        .lastChange()
        .filter(lastChange -> lastChange.id() > topologyToUpdate.getLastCompletedChangeId())
        .ifPresent(
            lastChange -> {
              LOG.info("The cluster has for sure been updated with new id " + lastChange.id());

              topologyListeners.forEach(BrokerTopologyListener::completedClusterChange);
              topologyToUpdate.setLastCompletedChangeId(lastChange.id());
            });
  }
}
