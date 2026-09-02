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
 * record written under a previous application version, for the upgrade-readiness endpoint. Only the
 * minor version boundary matters: a patch-only difference, in either direction, is treated as
 * {@code MIGRATED} since a patch release never changes the record format, while a minor-version gap
 * means a real backlog is still in progress; the running version's pre-release suffix, if any, is
 * stripped before comparing so a non-release build doesn't look incompatible with itself.
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
   * @param currentVersion the version to compare {@code nextUnexportedRecordVersion} against,
   *     exposed so tests can exercise every outcome deterministically.
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
            nextUnexportedRecordVersion, SemanticVersion.withoutPreReleaseSuffix(currentVersion));
    return switch (result) {
      case Compatible.SameVersion same ->
          migrated(
              partitionId,
              "next unexported record is already on " + same.version().toMinorVersionString());
      case Compatible.PatchUpgrade patch ->
          migrated(
              partitionId,
              "next unexported record is already on " + patch.from().toMinorVersionString());
      case Incompatible.PatchDowngrade patch ->
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
}
