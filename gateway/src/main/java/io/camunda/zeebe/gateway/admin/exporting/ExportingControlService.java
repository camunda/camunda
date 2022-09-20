/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin.exporting;

import io.camunda.zeebe.gateway.admin.BrokerAdminRequest;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ExportingControlService implements ExportingControlApi {
  final BrokerClient brokerClient;

  public ExportingControlService(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  @Override
  public CompletableFuture<Void> pauseExporting() {
    final var topology = brokerClient.getTopologyManager().getTopology();
    return broadcastOnTopology(topology, BrokerAdminRequest::pauseExporting);
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

    final var leader = topology.getLeaderForPartition(partitionId);
    final var followers =
        Optional.ofNullable(topology.getFollowersForPartition(partitionId)).orElseGet(Set::of);
    final var requests =
        Stream.concat(Stream.of(leader), followers.stream())
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
      throw new InvalidTopologyException(
          "Found %s partitions but expected %s, current topology: %s"
              .formatted(partitions.size(), expectedPartitions, topology));
    }

    for (final var partition : partitions) {
      final var leaderId = topology.getLeaderForPartition(partition);

      if (leaderId == BrokerClusterState.UNKNOWN_NODE_ID
          || leaderId == BrokerClusterState.NODE_ID_NULL) {
        throw new InvalidTopologyException(
            "Leader %s of partition %s is not known, current topology: %s"
                .formatted(leaderId, partition, topology));
      }

      final var followers =
          Optional.ofNullable(topology.getFollowersForPartition(partition))
              .orElse(Collections.emptySet());
      for (final var follower : followers) {
        if (follower == BrokerClusterState.UNKNOWN_NODE_ID
            || follower == BrokerClusterState.NODE_ID_NULL) {
          throw new InvalidTopologyException(
              "Follower %s of partition %s is not known, current topology: %s"
                  .formatted(follower, partition, topology));
        }
      }

      final var memberCount = followers.size() + 1;
      if (memberCount != replicationFactor) {
        throw new InvalidTopologyException(
            "Expected %s members of partition %s but found %s, current topology: %s"
                .formatted(replicationFactor, partition, memberCount, topology));
      }
    }
  }

  static final class InvalidTopologyException extends IllegalStateException {
    InvalidTopologyException(final String message) {
      super(message);
    }
  }
}
