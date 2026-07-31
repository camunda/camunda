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
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every operation targets a single physical tenant (partition group). The {@code physicalTenantId}
 * selects the partition set the operation enumerates and is stamped on every outgoing broker
 * request so that routing resolves leaders from that group's topology.
 */
@NullMarked
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

  /**
   * Queries every member of every partition of the physical tenant and aggregates the reported
   * exporter phases into a single status. Every member is queried, not just the leaders, because
   * pause and resume are applied to all of them: backup tooling needs to know the pause took effect
   * everywhere, not only where exporting currently runs.
   */
  public CompletableFuture<ExportingStatus> getExportingStatus(final String physicalTenantId) {
    final var topology = brokerClient.getTopologyManager().getTopology(physicalTenantId);
    validateTopology(topology);

    final var responses =
        topology.getPartitions().stream()
            .flatMap(
                partitionId ->
                    membersOfPartition(topology, partitionId).stream()
                        .map(
                            brokerId -> {
                              final var request = new BrokerAdminRequest();
                              request.setPartitionGroup(physicalTenantId);
                              request.setBrokerId(brokerId);
                              request.setPartitionId(partitionId);
                              request.getExportingState();
                              return brokerClient
                                  .sendRequest(request)
                                  .thenApply(
                                      response ->
                                          new String(
                                              response.getResponseOrThrow().getPayload(),
                                              StandardCharsets.UTF_8));
                            }))
            .toList();

    return CompletableFuture.allOf(responses.toArray(CompletableFuture<?>[]::new))
        .thenApply(
            ignored ->
                ExportingStatus.aggregate(
                    responses.stream().map(CompletableFuture::join).collect(Collectors.toSet())));
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

    final var members = membersOfPartition(topology, partitionId);

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

  /**
   * Every member holding a copy of the partition: its leader, its followers and its inactive
   * members. Inactive members are included because they are only temporarily unhealthy -- they keep
   * their partition data and become active again after recovering, so their exporter phase is as
   * relevant as any other replica's, both when pausing and when reporting the status.
   */
  private Set<BrokerMemberId> membersOfPartition(
      final BrokerClusterState topology, final Integer partitionId) {
    final var leader = topology.getLeaderForPartition(partitionId);
    final var followers = topology.getFollowersForPartition(partitionId);
    final var inactive = topology.getInactiveNodesForPartition(partitionId);

    final var members = new HashSet<BrokerMemberId>(topology.getReplicationFactor());
    if (leader != null) {
      members.add(leader);
    }
    members.addAll(followers);
    members.addAll(inactive);
    return members;
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
