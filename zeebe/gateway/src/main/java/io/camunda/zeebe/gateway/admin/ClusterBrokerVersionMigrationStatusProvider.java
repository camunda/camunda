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
import io.camunda.cluster.migration.MigrationState;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reports whether every broker in the cluster is already running the current application version,
 * for the upgrade-readiness endpoint. Complements {@link ClusterExporterMigrationStatusProvider}'s
 * per-record check: that check alone only proves nothing written so far is behind, not that nothing
 * behind ever will be -- a broker that hasn't upgraded yet could still regain leadership of a
 * partition and append an old-version record afterward. Once every broker has upgraded, no such
 * broker exists anymore to do that, closing the gap.
 *
 * <p>Broker membership and version are cluster-wide facts, not scoped to any one physical tenant,
 * so this reports the same computed status under every known physical tenant, with no per-partition
 * fan-out and no admin request needed -- unlike its sibling providers, everything it needs is
 * already known locally from cluster topology gossip.
 */
@NullMarked
public class ClusterBrokerVersionMigrationStatusProvider implements MigrationStatusProvider {

  public static final String CONDITION_NAME = "brokerVersionMigrated";

  private final BrokerClient brokerClient;
  private final PhysicalTenantIds physicalTenantIds;

  public ClusterBrokerVersionMigrationStatusProvider(
      final BrokerClient brokerClient, final PhysicalTenantIds physicalTenantIds) {
    this.brokerClient = brokerClient;
    this.physicalTenantIds = physicalTenantIds;
  }

  @Override
  public String conditionName() {
    return CONDITION_NAME;
  }

  @Override
  public Map<String, MigrationConditionStatus> getMigrationStatus() {
    final var status = computeStatusSafely();
    final var statusesByPhysicalTenant = new LinkedHashMap<String, MigrationConditionStatus>();
    physicalTenantIds.known().forEach(tenantId -> statusesByPhysicalTenant.put(tenantId, status));
    return statusesByPhysicalTenant;
  }

  private MigrationConditionStatus computeStatusSafely() {
    try {
      return computeStatus();
    } catch (final Exception e) {
      return new MigrationConditionStatus(
          MigrationState.UNKNOWN,
          "failed to determine broker-version migration status: " + e.getMessage());
    }
  }

  private MigrationConditionStatus computeStatus() {
    final var topology = brokerClient.getTopologyManager().getTopology();
    if (topology == null) {
      return new MigrationConditionStatus(MigrationState.UNKNOWN, "cluster topology not yet known");
    }

    final var brokers = topology.getBrokers();
    if (brokers.isEmpty()) {
      return new MigrationConditionStatus(
          MigrationState.UNKNOWN, "no brokers known in the cluster topology yet");
    }

    final var currentVersion = VersionUtil.getVersion();
    final var worst =
        brokers.stream()
            .map(
                brokerId ->
                    statusForBroker(brokerId, topology.getBrokerVersion(brokerId), currentVersion))
            .max(Comparator.comparingInt(status -> precedence(status.state())))
            .orElseThrow();
    if (worst.state() == MigrationState.MIGRATED) {
      // every broker individually reports MIGRATED; report one clean summary rather than an
      // arbitrary single broker's detail
      return new MigrationConditionStatus(
          MigrationState.MIGRATED, "every broker is running " + currentVersion);
    }
    return worst;
  }

  /** {@code UNKNOWN > MIGRATION_IN_PROGRESS > MIGRATED}, matching every other condition here. */
  private static int precedence(final MigrationState state) {
    return switch (state) {
      case UNKNOWN -> 2;
      case MIGRATION_IN_PROGRESS -> 1;
      case MIGRATED -> 0;
    };
  }

  /**
   * Only the minor version boundary matters, mirroring {@code ExportingMigrationStatusCalculator}:
   * a patch-only difference, in either direction, doesn't mean this broker could still write in an
   * incompatible format, while a minor-version gap means it hasn't upgraded yet. Every broker's
   * gossiped version is its own raw {@code VersionUtil .getVersion()} (confirmed in {@code
   * Broker#createBrokerInfo}), so a non-release build's pre-release suffix is stripped from both
   * sides -- otherwise every broker in an all-SNAPSHOT cluster would look incompatible with itself.
   */
  private static MigrationConditionStatus statusForBroker(
      final BrokerMemberId brokerId,
      final @Nullable String brokerVersion,
      final String currentVersion) {
    if (brokerVersion == null) {
      return new MigrationConditionStatus(
          MigrationState.UNKNOWN, "broker " + brokerId + ": version not yet known");
    }

    final var result =
        VersionCompatibilityCheck.check(
            SemanticVersion.withoutPreReleaseSuffix(brokerVersion),
            SemanticVersion.withoutPreReleaseSuffix(currentVersion));
    return switch (result) {
      case Compatible.SameVersion same ->
          migrated(brokerId, "broker is on " + same.version().toMinorVersionString());
      case Compatible.PatchUpgrade patch ->
          migrated(brokerId, "broker is on " + patch.from().toMinorVersionString());
      case Incompatible.PatchDowngrade patch ->
          migrated(brokerId, "broker is on " + patch.from().toMinorVersionString());
      case Compatible.MinorUpgrade minor ->
          new MigrationConditionStatus(
              MigrationState.MIGRATION_IN_PROGRESS,
              "broker "
                  + brokerId
                  + " is on "
                  + minor.from().toMinorVersionString()
                  + ", not yet upgraded to "
                  + minor.to().toMinorVersionString());
      case Incompatible incompatible ->
          new MigrationConditionStatus(
              MigrationState.UNKNOWN,
              "broker " + brokerId + ": incompatible version path: " + incompatible);
      case Indeterminate indeterminate ->
          new MigrationConditionStatus(
              MigrationState.UNKNOWN,
              "broker " + brokerId + ": cannot determine version compatibility: " + indeterminate);
    };
  }

  private static MigrationConditionStatus migrated(
      final BrokerMemberId brokerId, final String detail) {
    return new MigrationConditionStatus(
        MigrationState.MIGRATED, "broker " + brokerId + ": " + detail);
  }
}
