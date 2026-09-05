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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports whether every partition replica's RocksDB state has migrated to the current application
 * version and been captured in a snapshot, per physical tenant.
 *
 * <p>Every replica of every partition, of every known physical tenant, is queried — leader,
 * followers, and inactive members — not just the leader: migration runs independently on every
 * replica, so a follower that has not yet migrated could still be promoted to leader after a
 * fail-over.
 */
@NullMarked
public class ClusterRocksDbMigrationStatusProvider implements MigrationStatusProvider {

  public static final String CONDITION_NAME = "rocksDbMigrated";
  private static final Duration DEFAULT_FETCH_TIMEOUT = Duration.ofSeconds(5);
  private static final Logger LOG =
      LoggerFactory.getLogger(ClusterRocksDbMigrationStatusProvider.class);

  private final BrokerClient brokerClient;
  private final PhysicalTenantIds physicalTenantIds;
  private final Duration fetchTimeout;

  public ClusterRocksDbMigrationStatusProvider(
      final BrokerClient brokerClient, final PhysicalTenantIds physicalTenantIds) {
    this(brokerClient, physicalTenantIds, DEFAULT_FETCH_TIMEOUT);
  }

  @VisibleForTesting
  ClusterRocksDbMigrationStatusProvider(
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
        physicalTenantIds, fetchTimeout, this::fetchTenantStatus, LOG, "RocksDB migration status");
  }

  private CompletableFuture<PartitionMigrationStatus> fetchTenantStatus(
      final String physicalTenantId) {
    final var topology = brokerClient.getTopologyManager().getTopology(physicalTenantId);

    // A partition (or one of its members) that the topology doesn't know about yet -- during
    // startup, a reconfiguration, or a transient gossip gap -- would otherwise be silently
    // skipped rather than counted against the aggregate: PartitionReplicas.allOf can only query
    // members the topology actually reports, so a not-yet-known partition contributes nothing,
    // and a tenant where every known partition happens to report MIGRATED would then be reported
    // as fully migrated even though one partition was never actually checked.
    try {
      TopologyValidation.validateTopology(topology);
    } catch (final IncompleteTopologyException e) {
      return CompletableFuture.completedFuture(
          new PartitionMigrationStatus(
              MigrationStatusCode.UNKNOWN,
              "physical tenant '"
                  + physicalTenantId
                  + "': incomplete topology: "
                  + e.getMessage()));
    }

    final var responses =
        topology.getPartitions().stream()
            .flatMap(
                partitionId ->
                    PartitionReplicas.allOf(topology, partitionId).stream()
                        .map(brokerId -> requestStatus(physicalTenantId, partitionId, brokerId)))
            .toList();

    return CompletableFuture.allOf(responses.toArray(CompletableFuture<?>[]::new))
        .thenApply(
            ignored ->
                ClusterMigrationStatusReader.aggregate(
                    responses.stream().map(CompletableFuture::join).toList()));
  }

  private CompletableFuture<PartitionMigrationStatus> requestStatus(
      final String physicalTenantId, final Integer partitionId, final BrokerMemberId brokerId) {
    final var request = new BrokerAdminRequest();
    request.setPartitionGroup(physicalTenantId);
    request.setBrokerId(brokerId);
    request.setPartitionId(partitionId);
    request.getMigrationStatus();
    return brokerClient
        .sendRequest(request)
        .thenApply(
            response -> MigrationStatusPayload.decode(response.getResponseOrThrow().getPayload()));
  }
}
