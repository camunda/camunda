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
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusPayload;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports whether every partition replica's RocksDB state has migrated to the current application
 * version and been captured in a snapshot, per physical tenant, for the upgrade-readiness endpoint
 * (camunda/product-hub#3067).
 *
 * <p>Every replica of every partition, of every known physical tenant, is queried — leader,
 * followers, and inactive members — not just the leader: migration runs independently on every
 * replica, so a follower that has not yet migrated could still be promoted to leader after a
 * fail-over.
 *
 * <p>Talks to brokers using {@link PartitionMigrationStatus}/{@link MigrationStatusCode} — the
 * wire-level protocol types, kept deliberately separate from {@link MigrationConditionStatus}/
 * {@link MigrationState}, which are the upgrade-readiness API/SPI types. This class is the boundary
 * between the two: it aggregates every replica's wire-level status internally, per tenant, then
 * maps each tenant's final result to the API type only in {@link #getMigrationStatus()}, so the
 * broker/gateway RPC layer never needs to depend on the {@code cluster} API module for this.
 *
 * <p>Unlike {@link ExportingRequestBroadcaster}, which callers invoke asynchronously with an
 * explicit physical tenant, this implements the synchronous {@link MigrationStatusProvider} SPI so
 * it can be collected alongside every other upgrade-readiness condition. All known physical tenants
 * are fetched concurrently under one shared timeout budget; a tenant whose fan-out does not finish
 * within that budget is reported as {@link MigrationState#UNKNOWN} on its own, without holding back
 * tenants that did finish in time — per the distributed-condition contract described in {@code
 * docs/adr/management/004-upgrade-readiness-actuator-solution-proposal.md}.
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
    final var futuresByPhysicalTenant =
        new LinkedHashMap<String, CompletableFuture<PartitionMigrationStatus>>();
    for (final var physicalTenantId : physicalTenantIds.known()) {
      futuresByPhysicalTenant.put(physicalTenantId, fetchTenantStatus(physicalTenantId));
    }

    // One shared timeout budget for every tenant's fan-out, run concurrently -- not one timeout
    // per tenant, which would let a large multi-tenant cluster's total latency grow with the
    // tenant count.
    try {
      CompletableFuture.allOf(futuresByPhysicalTenant.values().toArray(CompletableFuture<?>[]::new))
          .get(fetchTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      LOG.warn(
          "Not every physical tenant's RocksDB migration status could be determined within {}.",
          fetchTimeout,
          e);
    }

    final var statuses = new LinkedHashMap<String, MigrationConditionStatus>();
    futuresByPhysicalTenant.forEach(
        (physicalTenantId, future) ->
            statuses.put(physicalTenantId, resolve(physicalTenantId, future)));
    return statuses;
  }

  /**
   * Reports each tenant's best available answer independently: a tenant whose fan-out finished
   * (successfully or not) reports its actual result; a tenant still pending when the shared budget
   * ran out reports {@code UNKNOWN} on its own, without holding back tenants that did finish.
   */
  private MigrationConditionStatus resolve(
      final String physicalTenantId, final CompletableFuture<PartitionMigrationStatus> future) {
    if (future.isDone() && !future.isCompletedExceptionally()) {
      return toConditionStatus(future.join());
    }
    if (future.isCompletedExceptionally()) {
      return new MigrationConditionStatus(
          MigrationState.UNKNOWN,
          "physical tenant '" + physicalTenantId + "': failed to determine migration status");
    }
    return new MigrationConditionStatus(
        MigrationState.UNKNOWN,
        "physical tenant '"
            + physicalTenantId
            + "': timed out waiting for every partition replica to respond");
  }

  private CompletableFuture<PartitionMigrationStatus> fetchTenantStatus(
      final String physicalTenantId) {
    final var topology = brokerClient.getTopologyManager().getTopology(physicalTenantId);

    final var responses =
        topology.getPartitions().stream()
            .flatMap(
                partitionId ->
                    membersOfPartition(topology, partitionId).stream()
                        .map(brokerId -> requestStatus(physicalTenantId, partitionId, brokerId)))
            .toList();

    return CompletableFuture.allOf(responses.toArray(CompletableFuture<?>[]::new))
        .thenApply(ignored -> aggregate(responses.stream().map(CompletableFuture::join).toList()));
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

  /**
   * Combines every replica's status with {@code UNKNOWN > MIGRATION_IN_PROGRESS > MIGRATED}
   * precedence: any replica we can't confidently assess makes the whole tenant's condition {@code
   * UNKNOWN} rather than silently reporting a partial answer as though it were complete.
   */
  private static PartitionMigrationStatus aggregate(final List<PartitionMigrationStatus> statuses) {
    if (statuses.isEmpty()) {
      return new PartitionMigrationStatus(
          MigrationStatusCode.UNKNOWN, "no partitions found in the topology");
    }

    var overallCode = MigrationStatusCode.MIGRATED;
    for (final var status : statuses) {
      if (status.code() == MigrationStatusCode.UNKNOWN) {
        overallCode = MigrationStatusCode.UNKNOWN;
        break;
      }
      if (status.code() == MigrationStatusCode.MIGRATION_IN_PROGRESS) {
        overallCode = MigrationStatusCode.MIGRATION_IN_PROGRESS;
      }
    }

    final var detail =
        statuses.stream().map(PartitionMigrationStatus::detail).collect(Collectors.joining("; "));
    return new PartitionMigrationStatus(overallCode, detail);
  }

  /** Maps the wire-level protocol type to the upgrade-readiness API/SPI type. */
  private static MigrationConditionStatus toConditionStatus(final PartitionMigrationStatus status) {
    final var state =
        switch (status.code()) {
          case MIGRATED -> MigrationState.MIGRATED;
          case MIGRATION_IN_PROGRESS -> MigrationState.MIGRATION_IN_PROGRESS;
          case UNKNOWN -> MigrationState.UNKNOWN;
        };
    return new MigrationConditionStatus(state, status.detail());
  }
}
