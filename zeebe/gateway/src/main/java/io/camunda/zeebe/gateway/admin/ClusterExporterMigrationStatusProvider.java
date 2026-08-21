/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusPayload;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports whether every exporter on every partition, per physical tenant, has finished exporting
 * and acknowledging every record written under a previous application version, for the
 * upgrade-readiness endpoint. Queries one reachable replica per partition — preferring the leader,
 * falling back to another replica on failure or timeout — since a follower's exporter position can
 * only lag the leader's, never diverge from it.
 */
@NullMarked
public class ClusterExporterMigrationStatusProvider implements MigrationStatusProvider {

  public static final String CONDITION_NAME = "exporterMigrated";
  private static final Duration DEFAULT_FETCH_TIMEOUT = Duration.ofSeconds(5);
  private static final Logger LOG =
      LoggerFactory.getLogger(ClusterExporterMigrationStatusProvider.class);

  private final BrokerClient brokerClient;
  private final PhysicalTenantIds physicalTenantIds;
  private final Duration fetchTimeout;

  public ClusterExporterMigrationStatusProvider(
      final BrokerClient brokerClient, final PhysicalTenantIds physicalTenantIds) {
    this(brokerClient, physicalTenantIds, DEFAULT_FETCH_TIMEOUT);
  }

  @VisibleForTesting
  ClusterExporterMigrationStatusProvider(
      final BrokerClient brokerClient,
      final PhysicalTenantIds physicalTenantIds,
      final Duration fetchTimeout) {
    this.brokerClient = brokerClient;
    this.physicalTenantIds = physicalTenantIds;
    this.fetchTimeout = fetchTimeout;
  }

  @Override
  public String conditionName() {
    return CONDITION_NAME;
  }

  @Override
  public Map<String, MigrationConditionStatus> getMigrationStatus() {
    return ClusterMigrationStatusReader.resolveTenants(
        physicalTenantIds, fetchTimeout, this::fetchTenantStatus, LOG, "exporter migration status");
  }

  private CompletableFuture<PartitionMigrationStatus> fetchTenantStatus(
      final String physicalTenantId) {
    // The topology manager may not know this physical tenant yet, so guard against a null
    // topology rather than letting it fail every tenant's fan-out for this poll.
    final var topology = brokerClient.getTopologyManager().getTopology(physicalTenantId);
    if (topology == null) {
      return CompletableFuture.completedFuture(
          new PartitionMigrationStatus(
              MigrationStatusCode.UNKNOWN,
              "physical tenant '" + physicalTenantId + "': topology not yet known"));
    }

    final var responses =
        topology.getPartitions().stream()
            .map(
                partitionId -> {
                  final var candidates = PartitionReplicas.allOf(topology, partitionId);
                  return requestStatusFromAnyReplica(
                      physicalTenantId, partitionId, candidates, perCandidateTimeout(candidates));
                })
            .toList();

    return CompletableFuture.allOf(responses.toArray(CompletableFuture<?>[]::new))
        .thenApply(
            ignored ->
                ClusterMigrationStatusReader.aggregate(
                    responses.stream().map(CompletableFuture::join).toList()));
  }

  /**
   * Splits the shared per-tenant {@link #fetchTimeout} evenly across a partition's candidate
   * replicas, so a single stalled candidate can't consume the whole shared budget by itself.
   */
  private Duration perCandidateTimeout(final List<BrokerMemberId> candidates) {
    return candidates.isEmpty() ? fetchTimeout : fetchTimeout.dividedBy(candidates.size());
  }

  /**
   * Tries every candidate replica of a partition in order — leader first — falling back to the next
   * one only if the current one fails or times out, since the first replica that answers is already
   * trustworthy.
   */
  private CompletableFuture<PartitionMigrationStatus> requestStatusFromAnyReplica(
      final String physicalTenantId,
      final int partitionId,
      final List<BrokerMemberId> candidates,
      final Duration perCandidateTimeout) {
    if (candidates.isEmpty()) {
      return CompletableFuture.completedFuture(
          new PartitionMigrationStatus(
              MigrationStatusCode.UNKNOWN,
              "partition " + partitionId + ": no known replica to query"));
    }

    final var first = candidates.get(0);
    final var rest = candidates.subList(1, candidates.size());
    return requestStatus(physicalTenantId, partitionId, first, perCandidateTimeout)
        .exceptionallyCompose(
            ignored ->
                requestStatusFromAnyReplica(
                    physicalTenantId, partitionId, rest, perCandidateTimeout));
  }

  private CompletableFuture<PartitionMigrationStatus> requestStatus(
      final String physicalTenantId,
      final Integer partitionId,
      final BrokerMemberId brokerId,
      final Duration requestTimeout) {
    final var request = new BrokerAdminRequest();
    request.setPartitionGroup(physicalTenantId);
    request.setBrokerId(brokerId);
    request.setPartitionId(partitionId);
    request.getExportingMigrationStatus();
    return brokerClient
        .sendRequest(request)
        .orTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .thenApply(
            response -> MigrationStatusPayload.decode(response.getResponseOrThrow().getPayload()));
  }
}
