/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every operation targets a single physical tenant (partition group). The {@code physicalTenantId}
 * selects the partition set the operation enumerates and is stamped on every outgoing broker
 * request so that routing resolves leaders from that group's topology.
 */
public class ExportingRequestBroadcaster {
  private static final Logger LOG = LoggerFactory.getLogger(ExportingRequestBroadcaster.class);
  final BrokerClient brokerClient;

  public ExportingRequestBroadcaster(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  public CompletableFuture<Void> pauseExporting(final String physicalTenantId) {
    LOG.info("Pausing exporting on all partitions of physical tenant {}.", physicalTenantId);
    final var topology = brokerClient.getTopologyManager().getTopology(physicalTenantId);
    return broadcastOnTopology(physicalTenantId, topology, BrokerAdminRequest::pauseExporting);
  }

  public CompletableFuture<Void> softPauseExporting(final String physicalTenantId) {
    LOG.info("Soft Pausing exporting on all partitions of physical tenant {}.", physicalTenantId);
    final var topology = brokerClient.getTopologyManager().getTopology(physicalTenantId);
    return broadcastOnTopology(physicalTenantId, topology, BrokerAdminRequest::softPauseExporting);
  }

  public CompletableFuture<Void> resumeExporting(final String physicalTenantId) {
    LOG.info("Resuming exporting on all partitions of physical tenant {}.", physicalTenantId);
    final var topology = brokerClient.getTopologyManager().getTopology(physicalTenantId);
    return broadcastOnTopology(physicalTenantId, topology, BrokerAdminRequest::resumeExporting);
  }

  private CompletableFuture<Void> broadcastOnTopology(
      final String physicalTenantId,
      final BrokerClusterState topology,
      final Consumer<BrokerAdminRequest> configureRequest) {
    validateTopology(topology);

    final var requests =
        topology.getPartitions().stream()
            .map(
                partition ->
                    broadcastOnPartition(physicalTenantId, topology, partition, configureRequest))
            .toArray(CompletableFuture<?>[]::new);
    return CompletableFuture.allOf(requests);
  }

  private CompletableFuture<Void> broadcastOnPartition(
      final String physicalTenantId,
      final BrokerClusterState topology,
      final Integer partitionId,
      final Consumer<BrokerAdminRequest> configureRequest) {

    final var leader = topology.getLeaderForPartition(partitionId);
    final var followers = topology.getFollowersForPartition(partitionId);
    final var inactive = topology.getInactiveNodesForPartition(partitionId);

    final var members = new HashSet<BrokerMemberId>(topology.getReplicationFactor());
    if (leader != null) {
      members.add(leader);
    }
    members.addAll(followers);
    members.addAll(inactive);

    final var requests =
        members.stream()
            .map(
                brokerId -> {
                  final var request = new BrokerAdminRequest();
                  request.setPartitionGroup(physicalTenantId);
                  request.setBrokerId(brokerId);
                  request.setPartitionId(partitionId);
                  configureRequest.accept(request);
                  return brokerClient
                      .sendRequest(request)
                      .thenApply(BrokerResponse::getResponseOrThrow);
                })
            .toArray(CompletableFuture<?>[]::new);
    return CompletableFuture.allOf(requests);
  }

  private void validateTopology(final BrokerClusterState topology) {
    final var replicationFactor = topology.getReplicationFactor();
    final var expectedPartitions = topology.getPartitionsCount();
    final var partitions = topology.getPartitions();

    if (partitions.size() != expectedPartitions) {
      throw new IncompleteTopologyException(
          "Found %s partitions but expected %s, current topology: %s"
              .formatted(partitions.size(), expectedPartitions, topology));
    }

    for (final var partition : partitions) {
      final var leaderId = topology.getLeaderForPartition(partition);

      if (leaderId == null) {
        throw new IncompleteTopologyException(
            "Leader of partition %s is not known, current topology: %s"
                .formatted(partition, topology));
      }

      final var followers = topology.getFollowersForPartition(partition);
      final var memberCount = followers.size() + 1;
      if (memberCount != replicationFactor) {
        throw new IncompleteTopologyException(
            "Expected %s members of partition %s but found %s, current topology: %s"
                .formatted(replicationFactor, partition, memberCount, topology));
      }
    }
  }
}
