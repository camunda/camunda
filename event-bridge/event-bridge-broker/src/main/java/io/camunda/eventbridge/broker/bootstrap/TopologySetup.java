/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.utils.net.Address;
import io.camunda.eventbridge.broker.transport.coordinator.CoordinationRequestHandler;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.util.VersionUtil;
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
    final var brokerInfo = createBrokerInfo(localMemberId);

    topologyManager = new TopologyManagerImpl(membershipService, brokerInfo);
    actorScheduler.submitActor(topologyManager);

    // Bridge local raft leadership into the gateway's topology. The gateway BrokerTopologyManager
    // learns remote brokers' leadership from SWIM gossip events, but in this single-JVM deployment
    // it does not get a membership event for the local node's own BrokerInfo update. So whenever a
    // local partition's leader changes, re-ingest membership (which now includes the freshly
    // published local BrokerInfo) so getLeaderForPartition resolves local partitions too.
    if (gatewayTopologyManager instanceof final BrokerTopologyManagerImpl gateway) {
      topologyManager.addTopologyPartitionListener(
          (partitionId, leaderId) -> gateway.initializeTopologyFromMembership());
    }

    // Separate topology manager for the coordinator group: it publishes a second BrokerInfo (under
    // a group-specific member-property key), so the gateway resolves the coordinator partition's
    // leader independently of the data partitions — exactly how Zeebe routes per partition group.
    final var coordinatorBrokerInfo = createCoordinatorBrokerInfo(localMemberId);
    coordinatorTopologyManager = new TopologyManagerImpl(membershipService, coordinatorBrokerInfo);
    actorScheduler.submitActor(coordinatorTopologyManager);
    if (gatewayTopologyManager instanceof final BrokerTopologyManagerImpl gateway) {
      coordinatorTopologyManager.addTopologyPartitionListener(
          (partitionId, leaderId) -> gateway.initializeTopologyFromMembership());
    }

    LOG.info("Topology managers started for broker {}", localMemberId);
    return topologyManager;
  }

  /**
   * The coordinator-group topology manager (used by the coordinator partition to gossip its role).
   */
  TopologyManagerImpl getCoordinatorTopologyManager() {
    return coordinatorTopologyManager;
  }

  void stop() {
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

  private BrokerInfo createBrokerInfo(final MemberId localMemberId) {
    final var nodeId = parseNodeId(localMemberId.id());
    final var clusterCfg = properties.cluster();
    // Advertise the command API address other nodes (and the gateway) use to reach this broker.
    final var address =
        Address.from(clusterCfg.effectiveAdvertisedHost(), clusterCfg.commandApiPort());

    final var brokerInfo = new BrokerInfo(nodeId, null, address.toString());
    brokerInfo
        .setClusterSize(clusterCfg.clusterSize())
        .setPartitionsCount(properties.broker().partitionCount())
        .setReplicationFactor(properties.raft().replicationFactor());

    final var version = VersionUtil.getVersion();
    if (version != null && !version.isBlank()) {
      brokerInfo.setVersion(version);
    }

    return brokerInfo;
  }

  /**
   * BrokerInfo for the coordinator routing group (single partition). Tagged with the coordinator
   * partition group so the gateway maintains a separate per-group topology for it.
   */
  private BrokerInfo createCoordinatorBrokerInfo(final MemberId localMemberId) {
    final var nodeId = parseNodeId(localMemberId.id());
    final var clusterCfg = properties.cluster();
    final var address =
        Address.from(clusterCfg.effectiveAdvertisedHost(), clusterCfg.commandApiPort());

    final var brokerInfo = new BrokerInfo(nodeId, null, address.toString());
    brokerInfo
        .setPartitionGroup(CoordinationRequestHandler.COORDINATOR_ROUTING_GROUP)
        .setClusterSize(clusterCfg.clusterSize())
        .setPartitionsCount(1)
        .setReplicationFactor(properties.raft().replicationFactor());

    final var version = VersionUtil.getVersion();
    if (version != null && !version.isBlank()) {
      brokerInfo.setVersion(version);
    }

    return brokerInfo;
  }

  private int parseNodeId(final String nodeId) {
    try {
      return Integer.parseInt(nodeId.replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return 0;
    }
  }
}
