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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.exception.RdbmsClusterIdIncompatibleException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
    assertThatThrownBy(versionStore("not-a-semver", "8.10.0")::checkCompatibility)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot determine version compatibility");
  }

  @Test
  void shouldAbortWhenApplicationVersionIsNull() {
    // given
    final var store = new RdbmsSchemaVersionStore(mock(DataSource.class), "", null);

    // when / then
    assertThatThrownBy(store::checkCompatibility)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("applicationVersion is not configured");
  }

  @Test
  void shouldAbortWhenDataSourceIsNull() {
    // given
    final var store = new RdbmsSchemaVersionStore(null, "", "8.10.0");

    // when / then
    assertThatThrownBy(store::checkCompatibility)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dataSource is not configured");
  }

  @Test
  void shouldFailWhenCompatibilityCheckEncountersUnexpectedError() throws Exception {
    // given - getConnection() throws
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB connection refused"));
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when / then - unexpected error must not be swallowed
    assertThatThrownBy(store::checkCompatibility)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to determine current schema version");
  }

  // ---- version recording ----

  @Test
  void shouldFailWhenVersionRecordingFails() throws Exception {
    // given - getConnection() throws inside recordCurrentVersion
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB write refused"));
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when / then
    assertThatThrownBy(store::recordCurrentVersion)
        .isInstanceOf(IllegalStateException.class)
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

  // ---- cluster ID check ----

  @Test
  void shouldSkipClusterIdCheckWhenLocalClusterIdBlank() throws Exception {
    // given
    final var dataSource = mock(DataSource.class);
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when
    assertThat(store.checkClusterIdCompatibility("", true)).isFalse();
    assertThat(store.checkClusterIdCompatibility(null, true)).isFalse();

    // then - the datasource is never touched
    verify(dataSource, never()).getConnection();
  }

  @Test
  void shouldSkipClusterIdCheckWhenNoPreviousClusterIdRecorded() throws Exception {
    // given - fresh install, or upgrading from before this check existed
    final var store = clusterIdStore(null);

    // when / then - no exception, and the caller is told to record the value
    assertThat(store.checkClusterIdCompatibility("this-cluster", true)).isTrue();
  }

  @Test
  void shouldAllowMatchingClusterId() throws Exception {
    final var store = clusterIdStore("this-cluster");

    // nothing changed, so no redundant write is needed
    assertThat(store.checkClusterIdCompatibility("this-cluster", true)).isFalse();
  }

  @Test
  void shouldThrowOnClusterIdMismatchWhenRestrictionEnabled() throws Exception {
    // given
    final var store = clusterIdStore("other-cluster");

    // when / then
    assertThatThrownBy(() -> store.checkClusterIdCompatibility("this-cluster", true))
        .isInstanceOf(RdbmsClusterIdIncompatibleException.class)
        .hasMessageContaining("other-cluster")
        .hasMessageContaining("this-cluster");
  }

  @Test
  void shouldWarnOnClusterIdMismatchWhenRestrictionDisabled() throws Exception {
    // given
    final var store = clusterIdStore("other-cluster");

    // when / then - no exception; the new value is adopted going forward
    assertThat(store.checkClusterIdCompatibility("this-cluster", false)).isTrue();
  }

  @Test
  void shouldFailWhenClusterIdCheckEncountersUnexpectedError() throws Exception {
    // given
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB connection refused"));
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when / then
    assertThatThrownBy(() -> store.checkClusterIdCompatibility("this-cluster", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to determine current cluster ID");
  }

  @Test
  void shouldSkipClusterIdRecordingWhenBlank() throws Exception {
    // given
    final var dataSource = mock(DataSource.class);
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when
    store.recordClusterId("");
    store.recordClusterId(null);

    // then - the datasource is never touched
    verify(dataSource, never()).getConnection();
  }

  @Test
  void shouldFailWhenClusterIdRecordingFails() throws Exception {
    // given
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB write refused"));
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when / then
    assertThatThrownBy(() -> store.recordClusterId("this-cluster"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to update cluster ID");
  }

  @Test
  void shouldWarnRatherThanClaimSuccessWhenNoSchemaVersionRowExistsYet() throws Exception {
    // given - the UPDATE affects 0 rows, e.g. because recordCurrentVersion() never inserted the
    // row (unparseable application version, such as local development builds)
    final var dataSource = mock(DataSource.class);
    final var connection = mock(Connection.class);
    final var statement = mock(PreparedStatement.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeUpdate()).thenReturn(0);
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when / then - does not throw, and does not falsely report success
    store.recordClusterId("this-cluster");
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
   * Builds a {@link RdbmsSchemaVersionStore} whose {@link #resolveCurrentSchemaVersion} returns
   * {@code schemaVersion}, backed by a mock data source that yields a mock connection.
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

  /**
   * Builds a {@link RdbmsSchemaVersionStore} whose {@link #readClusterId} returns {@code
   * previousClusterId}, backed by a mock data source that yields a mock connection.
   */
  private static RdbmsSchemaVersionStore clusterIdStore(final String previousClusterId) {
    final var dataSource = mock(DataSource.class);
    try {
      when(dataSource.getConnection()).thenReturn(mock(Connection.class));
    } catch (final SQLException e) {
      throw new RuntimeException(e);
    }
    return new RdbmsSchemaVersionStore(dataSource, "", "8.10.0") {
      @Override
      protected String readClusterId(final Connection connection, final String prefix) {
        return previousClusterId;
      }
    };
  }
}
