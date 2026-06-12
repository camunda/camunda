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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.Actor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrokerTopologyManagerImpl extends Actor
    implements BrokerTopologyManager,
        ClusterMembershipEventListener,
        ClusterConfigurationUpdateListener {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerTopologyManagerImpl.class);

  // Per-group live+configured topology; replaced atomically on updates.
  // The outer key is partition group name; the inner key is broker member id.
  private final Map<String, Map<BrokerMemberId, BrokerInfo>> memberPropertiesPerGroup =
      new HashMap<>();

  // Immutable snapshot replaced atomically; keyed by partition group name.
  private volatile Map<String, BrokerClientTopologyImpl> topologyPerGroup = Map.of();

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

  @Override
  public BrokerClusterState getTopology(final String physicalTenantId) {
    return topologyPerGroup.getOrDefault(
        physicalTenantId, BrokerClientTopologyImpl.uninitialized());
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
          // Backfill listener with all known broker IDs across all groups
          memberPropertiesPerGroup.values().stream()
              .flatMap(m -> m.keySet().stream())
              .distinct()
              .forEach(listener::brokerAdded);
        });
  }

  @Override
  public void removeTopologyListener(final BrokerTopologyListener listener) {
    actor.run(() -> topologyListeners.remove(listener));
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
      for (final BrokerInfo brokerInfo : BrokerInfo.allFromProperties(member.properties())) {
        addBroker(brokerInfo);
      }
    }
  }

  private void addBroker(final BrokerInfo brokerInfo) {
    final var brokerMemberId = BrokerMemberId.from(brokerInfo.getZone(), brokerInfo.getNodeId());
    final var group = brokerInfo.getPartitionGroup();
    actor.run(
        () -> {
          // Notify listeners only on the first appearance of this broker across all groups.
          final boolean isNew =
              memberPropertiesPerGroup.values().stream()
                  .noneMatch(m -> m.containsKey(brokerMemberId));
          if (isNew) {
            topologyListeners.forEach(l -> l.brokerAdded(brokerMemberId));
          }

          memberPropertiesPerGroup
              .computeIfAbsent(group, g -> new HashMap<>())
              .put(brokerMemberId, brokerInfo);
          rebuildGroupTopology(group);
        });
  }

  private void removeBroker(final Member member) {
    final List<BrokerInfo> allInfos = BrokerInfo.allFromProperties(member.properties());
    final BrokerMemberId brokerMemberId;
    if (!allInfos.isEmpty()) {
      final var anyInfo = allInfos.getFirst();
      brokerMemberId = BrokerMemberId.from(anyInfo.brokerIdStr());
    } else {
      // no brokerInfo means we have not added it to the topology yet, or it could be a gateway.
      return;
    }

    actor.run(
        () -> {
          final Set<String> groupsToRebuild = new HashSet<>();
          memberPropertiesPerGroup.forEach(
              (group, groupMap) -> {
                if (groupMap.remove(brokerMemberId) != null) {
                  groupsToRebuild.add(group);
                }
              });

          if (!groupsToRebuild.isEmpty()) {
            topologyListeners.forEach(l -> l.brokerRemoved(brokerMemberId));
            groupsToRebuild.forEach(this::rebuildGroupTopology);
          }
        });
  }

  private void rebuildGroupTopology(final String group) {
    final var groupMembers = memberPropertiesPerGroup.getOrDefault(group, Map.of()).values();
    final var configuredState = currentConfiguredState();
    final var newGroupTopology =
        BrokerClientTopologyImpl.fromMemberProperties(groupMembers, configuredState);

    final Map<String, BrokerClientTopologyImpl> updated = new HashMap<>(topologyPerGroup);
    updated.put(group, newGroupTopology);
    topologyPerGroup = Map.copyOf(updated);

    // temp: only update metrics for default group until we support group-specific metrics
    if (Protocol.DEFAULT_PARTITION_GROUP_NAME.equals(group)) {
      updateMetrics(newGroupTopology);
    }
  }

  private ConfiguredClusterState currentConfiguredState() {
    return topologyPerGroup.values().stream()
        .findFirst()
        .map(BrokerClientTopologyImpl::configuredClusterState)
        .orElse(BrokerClientTopologyImpl.uninitialized().configuredClusterState());
  }

  @Override
  public String getName() {
    return "GatewayTopologyManager";
  }

  @Override
  protected void onActorStarted() {
    initializeTopologyFromMembership();
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    final Member subject = event.subject();
    final Type eventType = event.type();
    final List<BrokerInfo> allInfos = BrokerInfo.allFromProperties(subject.properties());

    if (allInfos.isEmpty()) {
      return;
    }

    final var brokerId = subject.id();

    switch (eventType) {
      case MEMBER_ADDED, METADATA_CHANGED -> {
        LOG.debug(
            "Received {} from broker {}, updating {} group(s)",
            eventType,
            brokerId,
            allInfos.size());
        for (final BrokerInfo brokerInfo : allInfos) {
          addBroker(brokerInfo);
        }
      }
      case MEMBER_REMOVED -> {
        LOG.debug("Received broker was removed {}.", brokerId);
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
    actor.run(() -> applyClusterConfiguration(clusterTopology));
  }

  private void applyClusterConfiguration(final ClusterConfiguration clusterTopology) {
    // Run the full comparison + listener-notification logic against the default group once.
    final var oldDefault =
        topologyPerGroup.getOrDefault(
            Protocol.DEFAULT_PARTITION_GROUP_NAME, BrokerClientTopologyImpl.uninitialized());
    final var updatedDefault = updateConfiguredClusterState(clusterTopology, oldDefault);
    final var newConfiguredState = updatedDefault.configuredClusterState();

    final Map<String, BrokerClientTopologyImpl> allGroups = new HashMap<>(topologyPerGroup);
    allGroups.put(Protocol.DEFAULT_PARTITION_GROUP_NAME, updatedDefault);

    // temp: apply the same configured state to all other known groups. This must be revisited when
    // we add support for group-specific cluster configurations.
    memberPropertiesPerGroup.keySet().stream()
        .filter(group -> !group.equals(Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .forEach(
            group -> {
              final var oldGroup =
                  topologyPerGroup.getOrDefault(group, BrokerClientTopologyImpl.uninitialized());
              allGroups.put(
                  group,
                  new BrokerClientTopologyImpl(oldGroup.liveClusterState(), newConfiguredState));
            });

    topologyPerGroup = Map.copyOf(allGroups);
    updateMetrics(updatedDefault);
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
