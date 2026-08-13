/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import org.jspecify.annotations.Nullable;

/**
 * Computes whether every exporter on a partition has finished exporting and acknowledging every
 * record written under a previous application version, for the upgrade-readiness endpoint
 * (camunda/product-hub#3067) — given only the already-durable facts {@link ExporterDirector}
 * gathers (whether any exporter is configured at all, and the version of the next record still
 * waiting to be exported, if any). Kept separate from {@link ExporterDirector}, which owns the
 * actor-bound {@code LogStream}/{@code ExportersState} access this needs but has no reason to also
 * own the version-compatibility policy.
 *
 * <p>Only the minor version boundary matters for this condition: {@link
 * VersionCompatibilityCheck#check} already guarantees a patch-only difference is always {@link
 * Compatible.PatchUpgrade} (never {@link Incompatible}), so a record one or more patches behind is
 * treated the same as one on the exact same version — {@code MIGRATED} — since a patch release
 * never changes the on-disk/wire record format. Only {@link Compatible.MinorUpgrade} means a real
 * backlog is still in progress. Every version named in a status detail is reported as {@code
 * major.minor} — never a patch number or a build's {@code -SNAPSHOT} qualifier.
 *
 * <p>A record's stamped {@code brokerVersion} is a {@code VersionInfo} (major.minor.patch only —
 * the wire format has no room for a pre-release qualifier), so it can never carry a {@code
 * -SNAPSHOT}-style suffix, while {@link VersionUtil#getVersion()} can on a non-release build.
 * {@link VersionCompatibilityCheck#check} compares the two literally, so this asymmetry is stripped
 * from the current version before comparing -- otherwise every comparison on a non-release build
 * would misclassify an already-caught-up record as {@link Incompatible.UseOfPreReleaseVersion}.
 * Patch precision is kept, so {@code check}'s own {@code PatchUpgrade}/{@code MinorUpgrade}
 * distinction still applies.
 */
final class ExportingMigrationStatusCalculator {

  private ExportingMigrationStatusCalculator() {}

  /**
   * @param nextUnexportedRecordVersion the {@code brokerVersion} of the next record still waiting
   *     to be exported, or {@code null} if every exporter is caught up to the log head
   */
  static PartitionMigrationStatus compute(
      final int partitionId,
      final boolean hasExporters,
      final @Nullable String nextUnexportedRecordVersion) {
    return compute(
        partitionId, hasExporters, nextUnexportedRecordVersion, VersionUtil.getVersion());
  }

  /**
   * @param currentVersion the version to compare {@code nextUnexportedRecordVersion} against —
   *     exposed only so tests can exercise every version-compatibility outcome deterministically,
   *     without depending on {@link VersionUtil#getVersion()}'s actual value in the running JVM.
   */
  @VisibleForTesting
  static PartitionMigrationStatus compute(
      final int partitionId,
      final boolean hasExporters,
      final @Nullable String nextUnexportedRecordVersion,
      final String currentVersion) {
    if (!hasExporters) {
      return migrated(partitionId, "no exporters configured");
    }
    if (nextUnexportedRecordVersion == null) {
      return migrated(partitionId, "every exporter is caught up to the log head");
    }

    final var result =
        VersionCompatibilityCheck.check(
            nextUnexportedRecordVersion, withoutPreReleaseSuffix(currentVersion));
    return switch (result) {
      case Compatible.SameVersion same ->
          migrated(
              partitionId,
              "next unexported record is already on " + same.version().toMinorVersionString());
      case Compatible.PatchUpgrade patch ->
          migrated(
              partitionId,
              "next unexported record is already on " + patch.from().toMinorVersionString());
      case Compatible.MinorUpgrade minor ->
          new PartitionMigrationStatus(
              MigrationStatusCode.MIGRATION_IN_PROGRESS,
              "partition "
                  + partitionId
                  + ": next unexported record is on "
                  + minor.from().toMinorVersionString()
                  + ", not yet caught up to "
                  + minor.to().toMinorVersionString());
      case Incompatible incompatible ->
          unknown(
              partitionId, "incompatible version path for next unexported record: " + incompatible);
      case Indeterminate indeterminate ->
          unknown(
              partitionId,
              "cannot determine version compatibility for next unexported record: "
                  + indeterminate);
    };
  }

  private static PartitionMigrationStatus migrated(final int partitionId, final String detail) {
    return new PartitionMigrationStatus(
        MigrationStatusCode.MIGRATED, "partition " + partitionId + ": " + detail);
  }

  private static PartitionMigrationStatus unknown(final int partitionId, final String detail) {
    return new PartitionMigrationStatus(
        MigrationStatusCode.UNKNOWN, "partition " + partitionId + ": " + detail);
  }

  /**
   * @return {@code version} stripped of any pre-release/build-metadata suffix (e.g. {@code
   *     8.10.0-SNAPSHOT} → {@code 8.10.0}), keeping major.minor.patch intact. Falls back to the
   *     raw, unparseable input if it cannot be parsed as a semantic version -- {@link
   *     VersionCompatibilityCheck#check} already reports that case as {@code Indeterminate}.
   */
  private static String withoutPreReleaseSuffix(final String version) {
    return SemanticVersion.parse(version)
        .map(sv -> sv.major() + "." + sv.minor() + "." + sv.patch())
        .orElse(version);
  }
}
