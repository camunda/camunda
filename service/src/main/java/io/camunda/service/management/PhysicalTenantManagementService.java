/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.management;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.PhysicalTenantScopedApiServices;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A common parent for management services that target one physical tenant only. Contains utilities
 * to broadcast requests to all partition leaders in the topology of that physical tenant.
 *
 * @param <T> The type of the management service.
 */
public abstract class PhysicalTenantManagementService<T extends PhysicalTenantManagementService<T>>
    extends PhysicalTenantScopedApiServices<T> {

  protected PhysicalTenantManagementService(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  /** Returns the topology of the physical tenant. */
  protected final BrokerClusterState getTopology() {
    return brokerClient.getTopologyManager().getTopology(getPhysicalTenantId());
  }

  /**
   * Returns the full topology of the physical tenant.
   *
   * @throws NoTopologyAvailableException when topology is not yet available.
   * @throws IncompleteTopologyException when the topology misses partitions or replicas.
   */
  protected final BrokerClusterState getFullTopologyOrThrow() {
    final var topology = getTopology();
    if (topology == null) {
      throw new NoTopologyAvailableException();
    }

    final int expectedPartitionCount = topology.getPartitionsCount();
    final int knownPartitions = topology.getPartitions().size();
    if (expectedPartitionCount != knownPartitions) {
      throw new IncompleteTopologyException(
          "Expected to find %d partitions, but found only %d partitions in topology."
              .formatted(expectedPartitionCount, knownPartitions));
    }

    for (final var partition : topology.getPartitions()) {
      final var followers = topology.getFollowersForPartition(partition);
      final var memberCount = followers.size() + 1;
      final var replicationFactor = topology.getReplicationFactor();
      if (memberCount != replicationFactor) {
        throw new IncompleteTopologyException(
            "Expected %s members of partition %s but found %s, current topology: %s"
                .formatted(replicationFactor, partition, memberCount, topology));
      }
    }

    return topology;
  }

  /**
   * Broadcasts a request to all partition leaders.
   *
   * @param requestCreator a function that creates a request for a given partition id
   * @return a future that completes successfully when all requests have been completed,
   *     exceptionally when at least one request fails.
   * @param <R> The type of {@link BrokerRequest} that is sent.
   */
  protected final <R> CompletableFuture<Set<R>> broadcastPartitionLeaders(
      final Function<Integer, BrokerRequest<R>> requestCreator) {
    final var partitions = getFullTopologyOrThrow().getPartitions();
    final var requests = partitions.stream().map(requestCreator);
    return broadcast(requests);
  }

  /**
   * Broadcasts a request to all partition members.
   *
   * @param requestCreator a function that creates a request for a given partition id and broker id
   * @return a future that completes successfully when all requests have been completed,
   *     exceptionally when at least one request fails.
   * @param <R> The type of {@link BrokerRequest} that is sent.
   * @throws IncompleteTopologyException if one partition is missing a leader or when {@link
   *     #getFullTopologyOrThrow()} itself throws.
   * @throws NoTopologyAvailableException when {@link #getFullTopologyOrThrow()} itself throws.
   */
  protected final <R> CompletableFuture<Set<R>> broadcastPartitionMembers(
      final BiFunction<Integer, BrokerMemberId, BrokerRequest<R>> requestCreator) {

    final var topology = getFullTopologyOrThrow();
    final var partitions = topology.getPartitions();

    record RequestTarget(BrokerMemberId memberId, int partitionId) {}

    final var requests =
        partitions.stream()
            .<RequestTarget>mapMulti(
                (partition, consumer) -> {
                  final var leader = topology.getLeaderForPartition(partition);
                  if (leader != null) {
                    consumer.accept(new RequestTarget(leader, partition));
                  } else {
                    throw new IncompleteTopologyException(
                        "Leader of partition %s is not known, current topology: %s"
                            .formatted(partition, topology));
                  }
                  for (final var follower : topology.getFollowersForPartition(partition)) {
                    consumer.accept(new RequestTarget(follower, partition));
                  }
                  for (final var inactive : topology.getInactiveNodesForPartition(partition)) {
                    consumer.accept(new RequestTarget(inactive, partition));
                  }
                })
            .map(
                requestTarget ->
                    requestCreator.apply(requestTarget.partitionId, requestTarget.memberId));

    return broadcast(requests);
  }

  private <R> CompletableFuture<Set<R>> broadcast(final Stream<BrokerRequest<R>> requests) {
    final var responses =
        requests
            .map(
                request -> {
                  request.setPartitionGroup(getPhysicalTenantId());
                  return brokerClient.sendRequestWithRetry(request);
                })
            .toList();
    return CompletableFuture.allOf(responses.toArray(CompletableFuture[]::new))
        .thenApply(
            ignored ->
                responses.stream()
                    .map(CompletableFuture::join)
                    .map(BrokerResponse::getResponseOrThrow)
                    .collect(Collectors.toSet()));
  }
}
