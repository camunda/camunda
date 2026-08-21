/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.migration.MigrationState;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CurrentSchemaVersion#toMigrationStatus()} — the schema-version-to-{@link
 * MigrationState} mapping shared across every schema-version-backed upgrade-readiness provider. No
 * I/O or mocking needed: exercised directly against hand-built {@link CurrentSchemaVersion}
 * instances.
 */
class CurrentSchemaVersionTest {

  @Test
  void shouldReportMigratedForSameVersion() {
    // given - schema=8.10.0, app=8.10.0
    final var status = CurrentSchemaVersion.available("", "8.10.0", "8.10.0").toMigrationStatus();

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(status.detail()).contains("8.10.0");
  }

  @Test
  void shouldReportMigrationInProgressForPatchUpgrade() {
    // given - schema=8.9.0, app=8.9.5 (not yet migrated to the running app's exact version)
    final var status = CurrentSchemaVersion.available("", "8.9.0", "8.9.5").toMigrationStatus();

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportMigrationInProgressForMinorUpgrade() {
    // given - schema=8.9.1, app=8.10.0
    final var status = CurrentSchemaVersion.available("", "8.9.1", "8.10.0").toMigrationStatus();

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportMigrationInProgressForFreshDatabase() {
    // given - no schema version recorded yet
    final var status = CurrentSchemaVersion.freshDatabase("myPrefix_").toMigrationStatus();

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
    assertThat(status.detail()).contains("fresh database").contains("myPrefix_");
  }

  @Test
  void shouldReportUnknownForIncompatibleUpgradePath() {
    // given - schema=8.9.0, app=8.11.0 (skipped 8.10) - a real problem, not "in progress"
    final var status = CurrentSchemaVersion.available("", "8.9.0", "8.11.0").toMigrationStatus();

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownForIndeterminateSchemaVersion() {
    // given - stored schema version is not a valid semantic version
    final var status =
        CurrentSchemaVersion.available("", "not-a-semver", "8.10.0").toMigrationStatus();

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownForUnparseableApplicationVersion() {
    // given - app=development (not a semantic version)
    final var status =
        CurrentSchemaVersion.available("", "8.9.0", "development").toMigrationStatus();

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(status.detail()).contains("development");
  }

  @Test
  void shouldReportUnknownForReadFailure() {
    // given - the underlying store could not read the schema version at all
    final var status =
        CurrentSchemaVersion.readFailure("", new RuntimeException("connection refused"))
            .toMigrationStatus();

    // then - a read failure must never throw; it must be reported as UNKNOWN
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(status.detail()).contains("connection refused");
  }
}
