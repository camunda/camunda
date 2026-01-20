/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin.exporting;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.gateway.admin.BrokerAdminRequest;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.agrona.collections.IntHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportingControlService implements ExportingControlApi {
  private static final Logger LOG = LoggerFactory.getLogger(ExportingControlService.class);
  final BrokerClient brokerClient;

  public ExportingControlService(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  @Override
  public CompletableFuture<Void> pauseExporting() {
    LOG.info("Pausing exporting on all partitions.");
    final var topology = brokerClient.getTopologyManager().getTopology();
    return broadcastOnTopology(topology, BrokerAdminRequest::pauseExporting);
  }

  @Override
  public CompletableFuture<Void> softPauseExporting() {
    LOG.info("Soft Pausing exporting on all partitions.");
    final var topology = brokerClient.getTopologyManager().getTopology();
    return broadcastOnTopology(topology, BrokerAdminRequest::softPauseExporting);
  }

  @Override
  public CompletableFuture<Void> resumeExporting() {
    LOG.info("Resuming exporting on all partitions.");
    final var topology = brokerClient.getTopologyManager().getTopology();
    return broadcastOnTopology(topology, BrokerAdminRequest::resumeExporting);
  }

  private CompletableFuture<Void> broadcastOnTopology(
      final BrokerClusterState topology, final Consumer<BrokerAdminRequest> configureRequest) {
    validateTopology(topology);

    final var requests =
        topology.getPartitions().stream()
            .map(partition -> broadcastOnPartition(topology, partition, configureRequest))
            .toArray(CompletableFuture<?>[]::new);
    return CompletableFuture.allOf(requests);
  }

  private CompletableFuture<Void> broadcastOnPartition(
      final BrokerClusterState topology,
      final Integer partitionId,
      final Consumer<BrokerAdminRequest> configureRequest) {

    final var leader =
        topology.getLeaderForPartition(new PartitionId("raft-partition", partitionId));
    final var followers =
        Optional.ofNullable(
                topology.getFollowersForPartition(new PartitionId("raft-partition", partitionId)))
            .orElseGet(Set::of);
    final var inactive =
        Optional.ofNullable(
                topology.getInactiveNodesForPartition(
                    new PartitionId("raft-partition", partitionId)))
            .orElseGet(Set::of);

    final var members = new IntHashSet(topology.getReplicationFactor());
    members.add(leader);
    members.addAll(followers);
    members.addAll(inactive);

    final var requests =
        members.stream()
            .map(
                brokerId -> {
                  final var request = new BrokerAdminRequest();
                  request.setBrokerId(brokerId);
                  request.setPartitionId(partitionId);
                  configureRequest.accept(request);
                  return brokerClient.sendRequest(request);
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
      final var leaderId =
          topology.getLeaderForPartition(new PartitionId("raft-partition", partition));

      if (leaderId == BrokerClusterState.UNKNOWN_NODE_ID
          || leaderId == BrokerClusterState.NODE_ID_NULL) {
        throw new IncompleteTopologyException(
            "Leader %s of partition %s is not known, current topology: %s"
                .formatted(leaderId, partition, topology));
      }

      final var followers =
          Optional.ofNullable(
                  topology.getFollowersForPartition(new PartitionId("raft-partition", partition)))
              .orElse(Collections.emptySet());
      for (final var follower : followers) {
        if (follower == BrokerClusterState.UNKNOWN_NODE_ID
            || follower == BrokerClusterState.NODE_ID_NULL) {
          throw new IncompleteTopologyException(
              "Follower %s of partition %s is not known, current topology: %s"
                  .formatted(follower, partition, topology));
        }
      }

      final var memberCount = followers.size() + 1;
      if (memberCount != replicationFactor) {
        throw new IncompleteTopologyException(
            "Expected %s members of partition %s but found %s, current topology: %s"
                .formatted(replicationFactor, partition, memberCount, topology));
      }
    }
  }
}
