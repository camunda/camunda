/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.utils.net.Address;
import io.camunda.eventbridge.broker.BrokerMembers;
import io.camunda.eventbridge.clustermetadata.transport.MetadataRequestHandler;
import io.camunda.eventbridge.consumergroups.transport.CoordinationRequestHandler;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.util.VersionUtil;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates BrokerInfo and TopologyManager. Publishes partition roles via SWIM gossip. */
final class TopologySetup {

  private static final Logger LOG = LoggerFactory.getLogger(TopologySetup.class);

  private final ClusterMembershipService membershipService;
  private final ActorSchedulingService actorScheduler;
  private final EventBridgeProperties properties;
  private final BrokerTopologyManager gatewayTopologyManager;

  private TopologyManagerImpl topologyManager;
  private TopologyManagerImpl coordinatorTopologyManager;
  private TopologyManagerImpl metadataTopologyManager;
  private final List<TopologyManagerImpl> topicTopologyManagers = new CopyOnWriteArrayList<>();

  TopologySetup(
      final ClusterMembershipService membershipService,
      final ActorSchedulingService actorScheduler,
      final EventBridgeProperties properties,
      final BrokerTopologyManager gatewayTopologyManager) {
    this.membershipService = membershipService;
    this.actorScheduler = actorScheduler;
    this.properties = properties;
    this.gatewayTopologyManager = gatewayTopologyManager;
  }

  TopologyManagerImpl start() {
    final var localMemberId = membershipService.getLocalMember().id();

    // The data partitions publish under the default partition group, while the coordinator and
    // metadata groups each publish a separate BrokerInfo under their own group-specific
    // member-property key — so the gateway resolves each group's partition leaders independently,
    // exactly how Zeebe routes per partition group.
    topologyManager = registerManager(brokerInfo(null, properties.broker().partitionCount()));
    coordinatorTopologyManager =
        registerManager(brokerInfo(CoordinationRequestHandler.COORDINATOR_ROUTING_GROUP, 1));
    metadataTopologyManager =
        registerManager(brokerInfo(MetadataRequestHandler.METADATA_ROUTING_GROUP, 1));

    LOG.info("Topology managers started for broker {}", localMemberId);
    return topologyManager;
  }

  /**
   * Creates a topology manager for the given BrokerInfo, schedules it, and bridges its local raft
   * leadership into the gateway's topology. The gateway BrokerTopologyManager learns remote
   * brokers' leadership from SWIM gossip events, but in this single-JVM deployment it does not get
   * a membership event for the local node's own BrokerInfo update — so whenever a local partition's
   * leader changes, re-ingest membership (which now includes the freshly published local
   * BrokerInfo) so getLeaderForPartition resolves local partitions too.
   */
  private TopologyManagerImpl registerManager(final BrokerInfo brokerInfo) {
    final var manager = new TopologyManagerImpl(membershipService, brokerInfo);
    actorScheduler.submitActor(manager);
    if (gatewayTopologyManager instanceof final BrokerTopologyManagerImpl gateway) {
      manager.addTopologyPartitionListener(
          (partitionId, leaderId) -> gateway.initializeTopologyFromMembership());
    }
    return manager;
  }

  /**
   * The coordinator-group topology manager (used by the coordinator partition to gossip its role).
   */
  TopologyManagerImpl getCoordinatorTopologyManager() {
    return coordinatorTopologyManager;
  }

  /** The metadata-group topology manager (used by the metadata partition to gossip its role). */
  TopologyManagerImpl getMetadataTopologyManager() {
    return metadataTopologyManager;
  }

  /**
   * Creates a topology manager for a per-topic Raft group, provisioned at runtime. Like the
   * coordinator group, it publishes a BrokerInfo under the topic group's own member-property key so
   * the gateway resolves that topic's partition leaders independently of the data partitions.
   */
  TopologyManagerImpl createTopicTopologyManager(
      final String topicGroup, final int partitionCount) {
    final var manager = registerManager(brokerInfo(topicGroup, partitionCount));
    topicTopologyManagers.add(manager);
    LOG.info(
        "Topic topology manager started for group {} ({} partitions)", topicGroup, partitionCount);
    return manager;
  }

  void stop() {
    for (final var manager : topicTopologyManagers) {
      try {
        manager.closeAsync().join();
      } catch (final Exception e) {
        LOG.warn("Error closing topic topology manager", e);
      }
    }
    topicTopologyManagers.clear();
    if (metadataTopologyManager != null) {
      try {
        metadataTopologyManager.closeAsync().join();
      } catch (final Exception e) {
        LOG.warn("Error closing metadata topology manager", e);
      }
      metadataTopologyManager = null;
    }
    if (coordinatorTopologyManager != null) {
      try {
        coordinatorTopologyManager.closeAsync().join();
      } catch (final Exception e) {
        LOG.warn("Error closing coordinator topology manager", e);
      }
      coordinatorTopologyManager = null;
    }
    if (topologyManager != null) {
      try {
        topologyManager.closeAsync().join();
      } catch (final Exception e) {
        LOG.warn("Error closing topology manager", e);
      }
      topologyManager = null;
    }
  }

  /**
   * Builds the BrokerInfo this node gossips for one routing group. {@code partitionGroup} is the
   * gateway routing-group tag — {@code null} for the default data group, or the coordinator /
   * metadata / per-topic group name — which keeps each group's per-group topology separate on the
   * gateway. The command API address is the one other nodes (and the gateway) use to reach this
   * broker.
   */
  private BrokerInfo brokerInfo(final String partitionGroup, final int partitionCount) {
    final var nodeId = BrokerMembers.nodeId(membershipService.getLocalMember().id());
    final var clusterCfg = properties.cluster();
    final var address =
        Address.from(clusterCfg.effectiveAdvertisedHost(), clusterCfg.commandApiPort());

    final var brokerInfo = new BrokerInfo(nodeId, null, address.toString());
    if (partitionGroup != null) {
      brokerInfo.setPartitionGroup(partitionGroup);
    }
    brokerInfo
        .setClusterSize(clusterCfg.clusterSize())
        .setPartitionsCount(partitionCount)
        .setReplicationFactor(properties.raft().replicationFactor());

    final var version = VersionUtil.getVersion();
    if (version != null && !version.isBlank()) {
      brokerInfo.setVersion(version);
    }

    return brokerInfo;
  }
}
