/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.PartitionRoleValues;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientTopologyImpl.ConfiguredClusterState;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.Actor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrokerTopologyManagerImpl extends Actor
    implements BrokerTopologyManager,
        ClusterMembershipEventListener,
        ClusterConfigurationUpdateListener {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerTopologyManagerImpl.class);
  private volatile BrokerClientTopologyImpl topology = BrokerClientTopologyImpl.uninitialized();
  private volatile ClusterConfiguration clusterConfiguration = ClusterConfiguration.uninitialized();
  private final Supplier<Set<Member>> membersSupplier;
  private final BrokerClientTopologyMetrics topologyMetrics;

  private final Set<BrokerTopologyListener> topologyListeners = new HashSet<>();

  private final Map<BrokerMemberId, BrokerInfo> memberProperties = new HashMap<>();

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
          memberProperties.keySet().forEach(listener::brokerAdded);
        });
  }

  @Override
  public void removeTopologyListener(final BrokerTopologyListener listener) {
    actor.run(() -> topologyListeners.remove(listener));
  }

  private void updateTopology(
      final Function<BrokerClientTopologyImpl, BrokerClientTopologyImpl> updater) {
    actor.run(
        () -> {
          final var updated = updater.apply(topology);
          topology = updated;
          updateMetrics(updated);
        });
  }

  /**
   * Seeds the topology with all brokers currently visible in the membership service. Safe to call
   * from any thread; mutations are routed through the actor. Idempotent.
   *
   * <p>Callers wiring this up against a {@code ClusterMembershipService} must invoke this AFTER
   * registering this instance as a listener, to close the race where a {@code MEMBER_ADDED} event
   * fires between the actor starting and the listener being attached.
   */
  public void initializeTopologyFromMembership() {
    final Set<Member> members = membersSupplier.get();
    if (members == null || members.isEmpty()) {
      return;
    }

    for (final Member member : members) {
      final BrokerInfo brokerInfo = BrokerInfo.fromProperties(member.properties());
      if (brokerInfo != null) {
        addBroker(member, brokerInfo);
      }
    }
  }

  private void addBroker(final Member member, final BrokerInfo brokerInfo) {
    final var brokerMemberId = BrokerMemberId.from(brokerInfo.getZone(), brokerInfo.getNodeId());
    actor.run(
        () -> {
          if (!memberProperties.containsKey(brokerMemberId)) {
            topologyListeners.forEach(l -> l.brokerAdded(brokerMemberId));
          }

          memberProperties.put(brokerMemberId, brokerInfo);

          updateTopology(
              oldTopology ->
                  BrokerClientTopologyImpl.fromMemberProperties(
                      memberProperties.values(), oldTopology.configuredClusterState()));
        });
  }

  private void removeBroker(final Member member) {
    final var brokerInfo = BrokerInfo.fromProperties(member.properties());
    if (brokerInfo == null) {
      return;
    }

    final var brokerMemberId = BrokerMemberId.from(brokerInfo.getZone(), brokerInfo.getNodeId());
    actor.run(
        () -> {
          memberProperties.remove(brokerMemberId);
          topologyListeners.forEach(l -> l.brokerRemoved(brokerMemberId));

          final var oldTopology = topology;
          topology =
              BrokerClientTopologyImpl.fromMemberProperties(
                  memberProperties.values(), oldTopology.configuredClusterState());
        });
  }

  @Override
  public String getName() {
    return "GatewayTopologyManager";
  }

  @Override
  protected void onActorStarted() {
    // Get the initial member state before the listener is registered.
    // Also called AFTER registering this as a membership listener, to cover the window
    // between the snapshot here and listener attachment.
    initializeTopologyFromMembership();
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    final Member subject = event.subject();
    final Type eventType = event.type();
    final BrokerInfo brokerInfo = BrokerInfo.fromProperties(subject.properties());

    if (brokerInfo == null) {
      return;
    }
    final var brokerId = BrokerMemberId.from(brokerInfo.getZone(), brokerInfo.getNodeId());

    switch (eventType) {
      case MEMBER_ADDED -> {
        LOG.debug(
            "Received new broker {} : partitions {}, terms {} and health {}",
            brokerInfo,
            brokerInfo.getPartitionRoles(),
            brokerInfo.getPartitionLeaderTerms(),
            brokerInfo.getPartitionHealthStatuses());
        addBroker(subject, brokerInfo);
      }
      case METADATA_CHANGED -> {
        LOG.debug(
            "Received metadata change from Broker {}, partitions {}, terms {} and health {}.",
            brokerId,
            brokerInfo.getPartitionRoles(),
            brokerInfo.getPartitionLeaderTerms(),
            brokerInfo.getPartitionHealthStatuses());
        addBroker(subject, brokerInfo);
      }
      case MEMBER_REMOVED -> {
        LOG.debug("Received broker was removed {}.", brokerInfo);
        removeBroker(subject);
      }
      default -> LOG.debug("Received {} for broker {}, do nothing.", eventType, brokerId);
    }
  }

  private void updateMetrics(final BrokerClusterState topology) {
    final var partitions = topology.getPartitions();
    partitions.forEach(
        partition -> {
          final var leader = topology.getLeaderForPartition(partition);
          if (leader != null) {
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

    updateTopology(oldTopology -> updateConfiguredClusterState(clusterTopology, oldTopology));
  }

  private BrokerClientTopologyImpl updateConfiguredClusterState(
      final ClusterConfiguration clusterTopology, final BrokerClientTopologyImpl oldTopology) {
    final var newClusterSize = clusterTopology.clusterSize();
    final var newPartitionsCount = clusterTopology.partitionCount();
    final var newReplicationFactor = clusterTopology.minReplicationFactor();
    // cluster id is not expected to be null as it is always initialized in the cluster topology.
    // Unless the persisted topology is modified manually, and set to null.
    final var clusterId = clusterTopology.clusterId().orElse("");
    final long newLastChange =
        clusterTopology
            .lastChange()
            .filter(lastChange -> lastChange.id() > oldTopology.getLastCompletedChangeId())
            .map(
                lastChange -> {
                  topologyListeners.forEach(BrokerTopologyListener::completedClusterChange);
                  LOG.debug("Updating topology with last completed change id {}", lastChange.id());
                  return lastChange.id();
                })
            .orElse(oldTopology.getLastCompletedChangeId());

    if (oldTopology.configuredClusterState().incarnationNumber()
        != clusterTopology.incarnationNumber()) {
      topologyListeners.forEach(BrokerTopologyListener::clusterIncarnationChanged);
    }

    if (newClusterSize != oldTopology.getClusterSize()
        || newPartitionsCount != oldTopology.getPartitionsCount()
        || newReplicationFactor != oldTopology.getReplicationFactor()
        || newLastChange != oldTopology.getLastCompletedChangeId()) {
      LOG.debug(
          "Updating topology with clusterSize {}, partitionsCount {} and replicationFactor {}",
          newClusterSize,
          newPartitionsCount,
          newReplicationFactor);

      final var partitionIds = clusterTopology.partitionIds().boxed().toList();

      final var newClusterInfo =
          new ConfiguredClusterState(
              newClusterSize,
              newPartitionsCount,
              newReplicationFactor,
              partitionIds,
              newLastChange,
              clusterId,
              clusterTopology.incarnationNumber());

      return new BrokerClientTopologyImpl(oldTopology.liveClusterState(), newClusterInfo);
    }

    return oldTopology;
  }
}
