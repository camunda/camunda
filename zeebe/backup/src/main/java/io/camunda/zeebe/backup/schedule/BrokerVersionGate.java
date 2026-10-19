/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import org.jspecify.annotations.Nullable;

/**
 * Tells whether every broker of the cluster runs at least the current minor version (spec section
 * 18.1). A new record type, such as {@code LOG_ARCHIVE}, must not be written before that: a broker
 * that has not been upgraded yet could become leader or follower of a partition and fail to apply
 * it.
 *
 * <p>The version comparison mirrors the gateway's {@code
 * ClusterBrokerVersionMigrationStatusProvider}: only the minor version boundary matters, and the
 * pre-release suffix is stripped from both sides, so that an all-SNAPSHOT cluster counts as
 * upgraded. Unlike that provider, a broker on a newer minor or major version counts as upgraded as
 * well, since it can apply everything the current version writes.
 *
 * <p>A broker counts as not upgraded if its version is unknown or cannot be parsed, and a broker
 * missing from the topology counts as not upgraded too: fewer known brokers than the configured
 * cluster size closes the gate.
 */
public final class BrokerVersionGate {

  private BrokerVersionGate() {}

  /**
   * @param topology the cluster topology as gossiped to this broker; {@code null} if unknown
   * @param currentVersion the version of this broker, i.e. the version being upgraded to
   * @return {@code true} only if all configured brokers are known and run at least the current
   *     minor version
   */
  public static boolean allBrokersUpgraded(
      final @Nullable BrokerClusterState topology, final String currentVersion) {
    if (topology == null || !topology.isInitialized()) {
      return false;
    }
    final var brokers = topology.getBrokers();
    if (brokers.isEmpty() || brokers.size() < topology.getClusterSize()) {
      return false;
    }
    return brokers.stream()
        .allMatch(broker -> isUpgraded(topology.getBrokerVersion(broker), currentVersion));
  }

  /**
   * @return {@code true} if a broker on {@code brokerVersion} runs at least the minor version of
   *     {@code currentVersion}
   */
  static boolean isUpgraded(final @Nullable String brokerVersion, final String currentVersion) {
    if (brokerVersion == null) {
      return false;
    }
    final var result =
        VersionCompatibilityCheck.check(
            SemanticVersion.withoutPreReleaseSuffix(brokerVersion),
            SemanticVersion.withoutPreReleaseSuffix(currentVersion));
    return switch (result) {
      case Compatible.SameVersion ignored -> true;
      case Compatible.PatchUpgrade ignored -> true;
      case Incompatible.PatchDowngrade ignored -> true;
      // the broker is ahead of this one; it can apply everything this one writes
      case Incompatible.MinorDowngrade ignored -> true;
      case Incompatible.MajorDowngrade ignored -> true;
      case Compatible.MinorUpgrade ignored -> false;
      case Incompatible.MajorUpgrade ignored -> false;
      case Incompatible.SkippedMinorVersion ignored -> false;
      case Incompatible.UseOfPreReleaseVersion ignored -> false;
      case Indeterminate ignored -> false;
    };
  }
}
