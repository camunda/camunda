/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.migration;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import java.util.Optional;

/**
 * The schema-version facts for one physical tenant's secondary-storage schema, read without side
 * effects (never throws, never writes), for the upgrade-readiness endpoint. Different storage
 * backends read the persisted version differently (e.g. a JDBC table vs. a search-engine metadata
 * document), but converge on this same shape and the same mapping to an upgrade-readiness
 * condition.
 */
public record CurrentSchemaVersion(
    CurrentSchemaVersion.Kind kind,
    String prefix,
    Optional<String> schemaVersion,
    Optional<String> stableApplicationVersion,
    Optional<String> detail) {

  public static CurrentSchemaVersion available(
      final String prefix, final String schemaVersion, final String stableApplicationVersion) {
    return new CurrentSchemaVersion(
        Kind.AVAILABLE,
        prefix,
        Optional.of(schemaVersion),
        Optional.of(stableApplicationVersion),
        Optional.empty());
  }

  public static CurrentSchemaVersion freshDatabase(final String prefix) {
    return new CurrentSchemaVersion(
        Kind.FRESH_DATABASE, prefix, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public static CurrentSchemaVersion readFailure(final String prefix, final Exception e) {
    return new CurrentSchemaVersion(
        Kind.READ_FAILURE,
        prefix,
        Optional.empty(),
        Optional.empty(),
        Optional.of("failed to read schema version: " + e.getMessage()));
  }

  /**
   * Maps these raw schema-version facts to the upgrade-readiness condition reported for one
   * physical tenant.
   *
   * <ul>
   *   <li>Schema version equals the application version ({@link Compatible.SameVersion}) → {@link
   *       MigrationState#MIGRATED}.
   *   <li>Schema version is one or more minors behind, on a supported upgrade path ({@link
   *       Compatible.PatchUpgrade}/{@link Compatible.MinorUpgrade}), or no version has been
   *       recorded yet (fresh database) → {@link MigrationState#MIGRATION_IN_PROGRESS}. This
   *       includes externally-managed or not-yet-initialized schemas that haven't been migrated by
   *       the operator's own tooling yet.
   *   <li>An illegal upgrade path ({@link Incompatible}), an unparseable version ({@link
   *       Indeterminate}), an unparseable application version, or any read failure (e.g. a
   *       connection error) → {@link MigrationState#UNKNOWN} — this is a "we don't know," not a "we
   *       know it's not done."
   * </ul>
   */
  public MigrationConditionStatus toMigrationStatus() {
    return switch (kind) {
      case AVAILABLE ->
          toMigrationStatus(
              VersionCompatibilityCheck.check(
                  schemaVersion.orElseThrow(), stableApplicationVersion.orElseThrow()));
      case FRESH_DATABASE ->
          new MigrationConditionStatus(
              MigrationState.MIGRATION_IN_PROGRESS,
              "no schema version recorded yet for prefix '" + prefix + "' (fresh database)");
      case READ_FAILURE ->
          new MigrationConditionStatus(MigrationState.UNKNOWN, detail.orElseThrow());
    };
  }

  private static MigrationConditionStatus toMigrationStatus(
      final VersionCompatibilityCheck.CheckResult result) {
    return switch (result) {
      case final Compatible.SameVersion same ->
          new MigrationConditionStatus(
              MigrationState.MIGRATED,
              "schema version " + same.version() + " matches the application version");
      case final Compatible.PatchUpgrade patch ->
          new MigrationConditionStatus(
              MigrationState.MIGRATION_IN_PROGRESS,
              "schema version " + patch.from() + " has not yet migrated to " + patch.to());
      case final Compatible.MinorUpgrade minor ->
          new MigrationConditionStatus(
              MigrationState.MIGRATION_IN_PROGRESS,
              "schema version " + minor.from() + " has not yet migrated to " + minor.to());
      case final Incompatible incompatible ->
          new MigrationConditionStatus(
              MigrationState.UNKNOWN, "incompatible schema upgrade path: " + incompatible);
      case final Indeterminate indeterminate ->
          new MigrationConditionStatus(
              MigrationState.UNKNOWN,
              "cannot determine schema version compatibility: " + indeterminate);
    };
  }

  public enum Kind {
    AVAILABLE,
    FRESH_DATABASE,
    READ_FAILURE
  }
}
