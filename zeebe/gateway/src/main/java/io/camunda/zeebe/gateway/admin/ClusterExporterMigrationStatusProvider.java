/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.MigrationConditionStatus;
import io.camunda.cluster.MigrationState;
import io.camunda.cluster.MigrationStatusProvider;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusPayload;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports whether every exporter on every partition, per physical tenant, has finished exporting
 * and acknowledging every record written under a previous application version, for the
 * upgrade-readiness endpoint (camunda/product-hub#3067).
 *
 * <p>Unlike {@link ClusterRocksDbMigrationStatusProvider}, which must query every replica of every
 * partition because migration genuinely runs independently on each one, exporting only ever runs on
 * the partition leader — a follower purely mirrors the leader's exporter positions on a delay (see
 * {@code ExporterDirector}'s {@code ExporterMode.PASSIVE}), so its answer can only lag the
 * leader's, never diverge from or run ahead of it. Querying one reachable replica per partition,
 * preferring the leader and falling back to another replica on failure, is therefore enough to get
 * a confident, never falsely-optimistic answer, without the cost of querying every replica.
 *
 * <p>Talks to brokers using {@link PartitionMigrationStatus}/{@link MigrationStatusCode} — the
 * wire-level protocol types, kept deliberately separate from {@link MigrationConditionStatus}/
 * {@link MigrationState}, which are the upgrade-readiness API/SPI types — mapped at the boundary in
 * {@link ClusterMigrationStatusReader#toConditionStatus}.
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
    final var topology = brokerClient.getTopologyManager().getTopology(physicalTenantId);

    final var responses =
        topology.getPartitions().stream()
            .map(
                partitionId ->
                    requestStatusFromAnyReplica(
                        physicalTenantId,
                        partitionId,
                        PartitionReplicas.preferenceOrderOf(topology, partitionId)))
            .toList();

    return CompletableFuture.allOf(responses.toArray(CompletableFuture<?>[]::new))
        .thenApply(
            ignored ->
                ClusterMigrationStatusReader.aggregate(
                    responses.stream().map(CompletableFuture::join).toList()));
  }

  /**
   * Tries every candidate replica of a partition in order — leader first — falling back to the next
   * candidate only if the current one fails, rather than querying every replica in parallel: a
   * follower can only lag the leader for this condition, never disagree with or outrun it, so the
   * first replica that actually answers is already trustworthy.
   */
  private CompletableFuture<PartitionMigrationStatus> requestStatusFromAnyReplica(
      final String physicalTenantId, final int partitionId, final List<BrokerMemberId> candidates) {
    if (candidates.isEmpty()) {
      return CompletableFuture.completedFuture(
          new PartitionMigrationStatus(
              MigrationStatusCode.UNKNOWN,
              "partition " + partitionId + ": no known replica to query"));
    }

    final var first = candidates.get(0);
    final var rest = candidates.subList(1, candidates.size());
    return requestStatus(physicalTenantId, partitionId, first)
        .exceptionallyCompose(
            ignored -> requestStatusFromAnyReplica(physicalTenantId, partitionId, rest));
  }

  private CompletableFuture<PartitionMigrationStatus> requestStatus(
      final String physicalTenantId, final Integer partitionId, final BrokerMemberId brokerId) {
    final var request = new BrokerAdminRequest();
    request.setPartitionGroup(physicalTenantId);
    request.setBrokerId(brokerId);
    request.setPartitionId(partitionId);
    request.getExportingMigrationStatus();
    return brokerClient
        .sendRequest(request)
        .thenApply(
            response -> MigrationStatusPayload.decode(response.getResponseOrThrow().getPayload()));
  }
}
