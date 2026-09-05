/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIndeterminateException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionUnreadableException;
import io.camunda.zeebe.util.migration.CurrentSchemaVersion.Kind;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Unit tests for the schema-version read/check/write concern extracted into a dedicated store. */
class RdbmsSchemaVersionStoreTest {

  // ---- compatibility check ----

  @Test
  void shouldRejectSkippedMinorVersion() {
    // given - schema=8.9.0, app=8.11.0 (skipped 8.10)
    final var store = versionStore("8.9.0", "8.11.0");

    // when / then
    assertThatThrownBy(store::checkCompatibility)
        .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class)
        .hasMessageContaining("8.9.0")
        .hasMessageContaining("8.11.0");
  }

  @Test
  void shouldRejectDowngrade() {
    // given - schema=8.10.0, app=8.9.0
    final var store = versionStore("8.10.0", "8.9.0");

    // when / then
    assertThatThrownBy(store::checkCompatibility)
        .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class);
  }

  @Test
  void shouldAllowMinorUpgrade() {
    // given - schema=8.9.1, app=8.10.0
    final var store = versionStore("8.9.1", "8.10.0");

    // when / then - no exception
    store.checkCompatibility();
  }

  @Test
  void shouldAllowPatchUpgrade() {
    // given - schema=8.9.0, app=8.9.5
    versionStore("8.9.0", "8.9.5").checkCompatibility();
  }

  @Test
  void shouldAllowSameVersion() {
    versionStore("8.10.0", "8.10.0").checkCompatibility();
  }

  @Test
  void shouldTreatFreshDatabaseAsNoVersionCheck() {
    // given - resolveCurrentSchemaVersion returns null (fresh DB)
    versionStore(null, "8.11.0").checkCompatibility();
  }

  @Test
  void shouldAllowUpgradeFromInferredPreVersioningSchema() {
    // given: RDBMS_SCHEMA_VERSION doesn't exist but EXPORTER_POSITION does → inferred 8.9.0
    versionStore(RdbmsSchemaVersionStore.INFERRED_PRE_VERSIONING_SCHEMA_VERSION, "8.10.0")
        .checkCompatibility();
  }

  @Test
  void shouldStripSnapshotSuffixBeforeVersionCheck() {
    // given: schema=8.9.0, app=8.10.0-SNAPSHOT → normalized to 8.10.0 → valid minor upgrade
    versionStore("8.9.0", "8.10.0-SNAPSHOT").checkCompatibility();
  }

  @Test
  void shouldRejectSkippedMinorVersionAfterSnapshotStripping() {
    // given: schema=8.9.0, app=8.11.0-SNAPSHOT → normalized to 8.11.0 → skipped minor
    assertThatThrownBy(versionStore("8.9.0", "8.11.0-SNAPSHOT")::checkCompatibility)
        .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class);
  }

  @Test
  void shouldSkipVersionCheckForUnparseableApplicationVersion() {
    // given: app=development (not a semantic version) - check is skipped
    versionStore("8.9.0", "development").checkCompatibility();
  }

  @Test
  void shouldAbortWhenVersionCheckIsIndeterminate() {
    // given: stored schema version is not a valid semantic version → Indeterminate result
    // then: an operator has to correct the stored value, so this must not read as retryable
    assertThatThrownBy(versionStore("not-a-semver", "8.10.0")::checkCompatibility)
        .isInstanceOf(RdbmsSchemaVersionIndeterminateException.class)
        .hasMessageContaining("Cannot determine version compatibility");
  }

  @Test
  void shouldAbortWhenApplicationVersionIsNull() {
    // given
    final var store = new RdbmsSchemaVersionStore(mock(DataSource.class), "", null);

    // when / then
    assertThatThrownBy(store::checkCompatibility)
        .isInstanceOf(RdbmsSchemaVersionIndeterminateException.class)
        .hasMessageContaining("applicationVersion is not configured");
  }

  @Test
  void shouldAbortWhenDataSourceIsNull() {
    // given
    final var store = new RdbmsSchemaVersionStore(null, "", "8.10.0");

    // when / then
    assertThatThrownBy(store::checkCompatibility)
        .isInstanceOf(RdbmsSchemaVersionIndeterminateException.class)
        .hasMessageContaining("dataSource is not configured");
  }

  @Test
  void shouldFailWhenCompatibilityCheckEncountersUnexpectedError() throws Exception {
    // given - getConnection() throws
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB connection refused"));
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when / then - unexpected error must not be swallowed, and must stay retryable: a refused
    // connection is repaired without an operator touching the schema
    assertThatThrownBy(store::checkCompatibility)
        .isInstanceOf(RdbmsSchemaVersionUnreadableException.class)
        .hasMessageContaining("Failed to determine current schema version");
  }

  // ---- version recording ----

  @Test
  void shouldFailWhenVersionRecordingFails() throws Exception {
    // given - getConnection() throws inside recordCurrentVersion
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB write refused"));
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when / then - retryable: re-running the initialization records the version again
    assertThatThrownBy(store::recordCurrentVersion)
        .isInstanceOf(RdbmsSchemaVersionUnreadableException.class)
        .hasMessageContaining("Failed to update schema version");
  }

  @Test
  void shouldSkipVersionRecordingForUnparseableApplicationVersion() throws Exception {
    // given: app=development → recordCurrentVersion must skip silently without touching the DB
    final var dataSource = mock(DataSource.class);
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "development");

    // when
    store.recordCurrentVersion();

    // then - the datasource is never touched
    verify(dataSource, never()).getConnection();
  }

  // ---- getCurrentSchemaVersion (upgrade-readiness facts) ----

  @Test
  void shouldReportAvailableCurrentSchemaVersion() {
    // given - schema=8.10.0, app=8.10.0
    final var currentSchemaVersion = versionStore("8.10.0", "8.10.0").getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.AVAILABLE);
    assertThat(currentSchemaVersion.schemaVersion()).contains("8.10.0");
    assertThat(currentSchemaVersion.stableApplicationVersion()).contains("8.10.0");
  }

  @Test
  void shouldNormalizeApplicationVersionForCurrentSchemaVersion() {
    // given - schema=8.9.0, app=8.10.0-SNAPSHOT
    final var currentSchemaVersion =
        versionStore("8.9.0", "8.10.0-SNAPSHOT").getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.AVAILABLE);
    assertThat(currentSchemaVersion.stableApplicationVersion()).contains("8.10.0");
  }

  @Test
  void shouldReportFreshDatabaseCurrentSchemaVersion() {
    // given - resolveCurrentSchemaVersion returns null (fresh DB, not yet initialized)
    final var currentSchemaVersion = versionStore(null, "8.11.0").getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.FRESH_DATABASE);
    assertThat(currentSchemaVersion.schemaVersion()).isEmpty();
  }

  @Test
  void shouldReportReadFailureCurrentSchemaVersion() throws Exception {
    // given - getConnection() throws
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB connection refused"));
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when
    final var currentSchemaVersion = store.getCurrentSchemaVersion();

    // then - a read failure must never throw; it must be reported as raw failure facts
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.READ_FAILURE);
    assertThat(currentSchemaVersion.detail())
        .hasValueSatisfying(s -> s.contains("DB connection refused"));
  }

  // ---- toStableVersion ----

  @Test
  void shouldNormalizeSnapshotVersionToStable() {
    assertThat(RdbmsSchemaVersionStore.toStableVersion("8.11.0-SNAPSHOT")).contains("8.11.0");
  }

  @Test
  void shouldReturnEmptyForUnparseableVersion() {
    assertThat(RdbmsSchemaVersionStore.toStableVersion("development")).isEmpty();
  }

  @Test
  void shouldReturnStableVersionUnchanged() {
    assertThat(RdbmsSchemaVersionStore.toStableVersion("8.10.0")).contains("8.10.0");
  }

  // ---- helpers ----

  /**
   * Builds a {@link RdbmsSchemaVersionStore} whose currentSchemaVersion returns {@code
   * schemaVersion}, backed by a mock data source that yields a mock connection.
   */
  private static RdbmsSchemaVersionStore versionStore(
      final String schemaVersion, final String appVersion) {
    final var dataSource = mock(DataSource.class);
    try {
      when(dataSource.getConnection()).thenReturn(mock(Connection.class));
    } catch (final SQLException e) {
      throw new RuntimeException(e);
    }
    return new RdbmsSchemaVersionStore(dataSource, "", appVersion) {
      @Override
      protected String resolveCurrentSchemaVersion(
          final Connection connection, final String prefix) {
        return schemaVersion;
      }
    };
  }
}
