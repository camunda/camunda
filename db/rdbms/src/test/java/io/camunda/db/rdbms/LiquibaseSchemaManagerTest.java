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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.LiquibaseSchemaManager.PerTenantLiquibase;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockService;
import org.junit.jupiter.api.Test;

class LiquibaseSchemaManagerTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  // ---- isInitialized / afterPropertiesSet ----

  @Test
  void shouldMarkInitializedTrueForEachTenantAfterMigration() throws Exception {
    // given
    final var configs =
        new LinkedHashMap<String, PerTenantSchemaConfig>(
            Map.of(
                TENANT_A, autoDdlConfig(),
                TENANT_B, autoDdlConfig()));
    final var schemaManager = new TestLiquibaseSchemaManager(configs, "8.10.0");

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
    assertThat(schemaManager.isInitialized(TENANT_B)).isTrue();
  }

  @Test
  void shouldReturnFalseFromIsInitializedForUnknownTenant() throws Exception {
    // given
    final var configs = Map.of(TENANT_A, autoDdlConfig());
    final var schemaManager = new TestLiquibaseSchemaManager(configs, "8.10.0");

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized("unknown")).isFalse();
  }

  @Test
  void shouldSkipMigrationAndStillMarkInitializedWhenAutoDdlFalse() throws Exception {
    // given
    final var configs = Map.of(TENANT_A, noAutoDdlConfig());
    final var schemaManager = spy(new TestLiquibaseSchemaManager(configs, "8.10.0"));

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
    verify(schemaManager, never()).performMigration(any());
  }

  @Test
  void shouldSkipVersionCheckWhenAutoDdlFalse() throws Exception {
    // given
    final var configs = Map.of(TENANT_A, noAutoDdlConfig());
    final var schemaManager = spy(new TestLiquibaseSchemaManager(configs, "8.10.0"));

    // when
    schemaManager.afterPropertiesSet();

    // then
    verify(schemaManager, never()).checkSchemaVersionCompatibility(any());
  }

  // ---- buildPerTenant — prefix and lock wait timeout ----

  @Test
  void shouldUsePerTenantPrefixForChangeLogTables() {
    // given
    final var schemaManager = new BuildPerTenantExposingSchemaManager();
    final var cfgA = configWithPrefix("A_");
    final var cfgB = configWithPrefix("B_");

    // when
    final var perTenantA = schemaManager.expose(TENANT_A, cfgA);
    final var perTenantB = schemaManager.expose(TENANT_B, cfgB);

    // then
    assertThat(perTenantA.prefix()).isEqualTo("A_");
    assertThat(perTenantB.prefix()).isEqualTo("B_");
  }

  @Test
  void shouldUsePerTenantDdlLockWaitTimeout() {
    // given
    final var cfgA = configWithDdlTimeout(Duration.ofMinutes(5));
    final var cfgB = configWithDdlTimeout(Duration.ofMinutes(30));
    final var schemaManager = new BuildPerTenantExposingSchemaManager();

    // when
    final var perTenantA = schemaManager.expose(TENANT_A, cfgA);
    final var perTenantB = schemaManager.expose(TENANT_B, cfgB);

    // then
    assertThat(perTenantA.ddlLockWaitTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(perTenantB.ddlLockWaitTimeout()).isEqualTo(Duration.ofMinutes(30));
  }

  // ---- failure handling ----

  @Test
  void shouldFailStartupWhenAnyTenantMigrationFails() throws Exception {
    // given - tenant A succeeds, tenant B fails. Insertion order matters here: A is migrated
    // first, B second (which throws). LinkedHashMap preserves the order; Map.of does not.
    final var configs = new LinkedHashMap<String, PerTenantSchemaConfig>();
    configs.put(TENANT_A, autoDdlConfig());
    configs.put(TENANT_B, autoDdlConfig());
    final var failingTenant = TENANT_B;
    final var schemaManager =
        new TestLiquibaseSchemaManager(configs, "8.10.0") {
          @Override
          protected void performMigration(final PerTenantLiquibase tenant) {
            if (tenant.physicalTenantId().equals(failingTenant)) {
              throw new RuntimeException("Migration failed for " + tenant.physicalTenantId());
            }
          }
        };

    // when / then
    assertThatThrownBy(schemaManager::afterPropertiesSet)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Migration failed for " + failingTenant);
  }

  @Test
  void shouldKeepInitializedAbsentForTenantWhenMigrationFails() {
    // given - tenant B's migration fails. Insertion order matters: A is migrated first
    // (succeeds), B second (throws). LinkedHashMap preserves the order; Map.of does not.
    final var configs = new LinkedHashMap<String, PerTenantSchemaConfig>();
    configs.put(TENANT_A, autoDdlConfig());
    configs.put(TENANT_B, autoDdlConfig());
    final var failingTenant = TENANT_B;
    final var schemaManager =
        new TestLiquibaseSchemaManager(configs, "8.10.0") {
          @Override
          protected void performMigration(final PerTenantLiquibase tenant) {
            if (tenant.physicalTenantId().equals(failingTenant)) {
              throw new RuntimeException("Migration failed for " + tenant.physicalTenantId());
            }
          }
        };

    // when
    assertThatThrownBy(schemaManager::afterPropertiesSet).isInstanceOf(RuntimeException.class);

    // then - tenant A completed, tenant B did not
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
    assertThat(schemaManager.isInitialized(TENANT_B)).isFalse();
  }

  // ---- stale lock (mock-based) ----

  @Test
  void shouldSkipStaleLockCheckWhenDataSourceIsNull() {
    // given - ddl timeout is set but dataSource is null
    final var tenant = new PerTenantLiquibase(TENANT_A, null, null, "", Duration.ofMinutes(10));
    final var schemaManager = new LiquibaseSchemaManager(Map.of(), "8.10.0");

    // when / then - should not throw when dataSource is null
    schemaManager.releaseStaleLockIfPresent(tenant);
  }

  @Test
  void shouldNotReleaseLockWhenNoLocksPresent() throws Exception {
    // given
    final var mockLockService = mock(LockService.class);
    when(mockLockService.listLocks()).thenReturn(new DatabaseChangeLogLock[0]);

    final var mockDataSource = mock(DataSource.class);
    when(mockDataSource.getConnection()).thenReturn(mock(Connection.class));
    final var tenant =
        new PerTenantLiquibase(TENANT_A, null, mockDataSource, "", Duration.ofMinutes(10));
    final var schemaManager = new MockLockServiceSchemaManager(mockLockService);

    // when
    schemaManager.releaseStaleLockIfPresent(tenant);

    // then - empty lock list → no release
    verify(mockLockService, never()).forceReleaseLock();
  }

  @Test
  void shouldContinueMigrationWhenStaleLockCheckThrowsException() throws Exception {
    // given - getConnection() throws inside releaseStaleLockIfPresent
    final var mockDataSource = mock(DataSource.class);
    when(mockDataSource.getConnection()).thenThrow(new RuntimeException("DB connection failed"));
    final var tenant =
        new PerTenantLiquibase(TENANT_A, null, mockDataSource, "", Duration.ofMinutes(10));
    final var schemaManager = new LiquibaseSchemaManager(Map.of(), "8.10.0");

    // when / then - exception should be swallowed, method should return normally
    schemaManager.releaseStaleLockIfPresent(tenant);
  }

  // ---- version compatibility / failure modes ----

  @Test
  void shouldAbortStartupWhenApplicationVersionIsNull() {
    // given
    final var configs = Map.of(TENANT_A, autoDdlConfig());
    final var schemaManager = new TestLiquibaseSchemaManager(configs, null);

    // when / then
    assertThatThrownBy(schemaManager::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("applicationVersion is not configured");
  }

  @Test
  void shouldRejectSkippedMinorVersion() {
    // given - schema=8.9.0, app=8.11.0 (skipped 8.10)
    final var schemaManager = versionCheckManager("8.9.0", "8.11.0");

    // when / then
    assertThatThrownBy(schemaManager::afterPropertiesSet)
        .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class)
        .hasMessageContaining("8.9.0")
        .hasMessageContaining("8.11.0");
    assertThat(schemaManager.isInitialized(TENANT_A)).isFalse();
  }

  @Test
  void shouldRejectDowngrade() {
    // given - schema=8.10.0, app=8.9.0
    final var schemaManager = versionCheckManager("8.10.0", "8.9.0");

    // when / then
    assertThatThrownBy(schemaManager::afterPropertiesSet)
        .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class);
  }

  @Test
  void shouldAllowMinorUpgrade() throws Exception {
    // given - schema=8.9.1, app=8.10.0
    final var schemaManager = versionCheckManager("8.9.1", "8.10.0");

    // when / then - no exception
    schemaManager.afterPropertiesSet();
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldAllowPatchUpgrade() throws Exception {
    // given - schema=8.9.0, app=8.9.5
    final var schemaManager = versionCheckManager("8.9.0", "8.9.5");

    // when / then - no exception
    schemaManager.afterPropertiesSet();
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldAllowSameVersion() throws Exception {
    // given - schema=8.10.0, app=8.10.0
    final var schemaManager = versionCheckManager("8.10.0", "8.10.0");

    // when / then - no exception
    schemaManager.afterPropertiesSet();
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldTreatFreshDatabaseAsNoVersionCheck() throws Exception {
    // given - resolveCurrentSchemaVersion returns null (fresh DB)
    final var schemaManager = versionCheckManager(null, "8.11.0");

    // when / then - no exception
    schemaManager.afterPropertiesSet();
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldAbortStartupWhenDataSourceIsNull() {
    // given - PerTenantLiquibase with null dataSource
    final var tenant = new PerTenantLiquibase(TENANT_A, null, null, "", null);
    final var schemaManager = new LiquibaseSchemaManager(Map.of(), "8.10.0");

    // when / then - null dataSource must abort startup
    assertThatThrownBy(() -> schemaManager.checkSchemaVersionCompatibility(tenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dataSource is not configured");
  }

  @Test
  void shouldInferPreVersioningSchemaWhenExporterPositionExistsButNoVersionTable()
      throws Exception {
    // given: RDBMS_SCHEMA_VERSION doesn't exist but EXPORTER_POSITION does → inferred 8.9.0
    final var schemaManager =
        versionCheckManager(
            LiquibaseSchemaManager.INFERRED_PRE_VERSIONING_SCHEMA_VERSION, "8.10.0");

    // when / then - inferred 8.9.0 → app 8.10.0 is a valid minor upgrade, no exception
    schemaManager.afterPropertiesSet();
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldFailStartupWhenVersionCheckEncountersUnexpectedError() throws Exception {
    // given - getConnection() throws inside checkSchemaVersionCompatibility
    final var mockDataSource = mock(DataSource.class);
    when(mockDataSource.getConnection()).thenThrow(new RuntimeException("DB connection refused"));
    final var tenant = new PerTenantLiquibase(TENANT_A, null, mockDataSource, "", null);
    final var schemaManager = new LiquibaseSchemaManager(Map.of(), "8.10.0");

    // when / then - unexpected error must not be swallowed; startup must fail
    assertThatThrownBy(() -> schemaManager.checkSchemaVersionCompatibility(tenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to determine current schema version");
  }

  @Test
  void shouldFailStartupWhenSchemaVersionUpdateFails() throws Exception {
    // given - getConnection() throws inside updateSchemaVersion
    final var mockDataSource = mock(DataSource.class);
    when(mockDataSource.getConnection()).thenThrow(new RuntimeException("DB write refused"));
    final var tenant = new PerTenantLiquibase(TENANT_A, null, mockDataSource, "", null);
    final var schemaManager = new LiquibaseSchemaManager(Map.of(), "8.10.0");

    // when / then - a failure in updateSchemaVersion must abort startup
    assertThatThrownBy(() -> schemaManager.updateSchemaVersion(tenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to update schema version");
  }

  @Test
  void shouldStripSnapshotSuffixBeforeVersionCheck() throws Exception {
    // given: schema=8.9.0, app=8.10.0-SNAPSHOT → normalized to 8.10.0 → valid minor upgrade
    final var schemaManager = versionCheckManager("8.9.0", "8.10.0-SNAPSHOT");

    // when / then - no exception; SNAPSHOT is stripped before the check
    schemaManager.afterPropertiesSet();
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldRejectSkippedMinorVersionAfterSnapshotStripping() {
    // given: schema=8.9.0, app=8.11.0-SNAPSHOT → normalized to 8.11.0 → skipped minor
    final var schemaManager = versionCheckManager("8.9.0", "8.11.0-SNAPSHOT");

    // when / then
    assertThatThrownBy(schemaManager::afterPropertiesSet)
        .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class);
  }

  @Test
  void shouldSkipVersionCheckForUnparseableApplicationVersion() throws Exception {
    // given: app=development (not a semantic version) - check is skipped, startup succeeds
    final var schemaManager = versionCheckManager("8.9.0", "development");

    // when / then - no exception; unparseable versions skip the check
    schemaManager.afterPropertiesSet();
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldSkipVersionStorageForUnparseableApplicationVersion() throws Exception {
    // given: app=development → updateSchemaVersion must skip silently without touching the DB
    final var mockDataSource = mock(DataSource.class);
    final var tenant = new PerTenantLiquibase(TENANT_A, null, mockDataSource, "", null);
    final var schemaManager = new LiquibaseSchemaManager(Map.of(), "development");

    // when / then - no exception, datasource is never touched
    schemaManager.updateSchemaVersion(tenant);
    verify(mockDataSource, never()).getConnection();
  }

  @Test
  void shouldAbortStartupWhenVersionCheckIsIndeterminate() {
    // given: stored schema version is not a valid semantic version → Indeterminate result
    final var schemaManager = versionCheckManager("not-a-semver", "8.10.0");

    // when / then - Indeterminate must abort startup
    assertThatThrownBy(schemaManager::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot determine version compatibility");
  }

  // ---- retry tests ----

  @Test
  void shouldRetryMigrationWhenDeadlockOccursAndThenSucceed() throws Exception {
    // given
    final var schemaManager =
        new RetryableMigrationSchemaManager(
            1,
            new RuntimeException(
                "liquibase migration failed",
                new SQLException(
                    "Transaction was deadlocked on lock resources and has been chosen as the deadlock victim.",
                    "40001",
                    1205)));

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
    assertThat(schemaManager.attempts).isEqualTo(2);
    assertThat(schemaManager.waits).isEqualTo(1);
  }

  @Test
  void shouldFailWhenDeadlockRetriesAreExhausted() {
    // given
    final var schemaManager =
        new RetryableMigrationSchemaManager(
            3,
            new RuntimeException(
                "liquibase migration failed", new SQLException("deadlock victim", "40001", 1205)));

    // when / then
    assertThatThrownBy(schemaManager::afterPropertiesSet)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("liquibase migration failed");

    assertThat(schemaManager.isInitialized(TENANT_A)).isFalse();
    assertThat(schemaManager.attempts).isEqualTo(3);
    assertThat(schemaManager.waits).isEqualTo(2);
  }

  @Test
  void shouldNotRetryWhenMigrationFailureIsNotDeadlock() {
    // given
    final var schemaManager =
        new RetryableMigrationSchemaManager(1, new RuntimeException("non-retryable failure"));

    // when / then
    assertThatThrownBy(schemaManager::afterPropertiesSet)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("non-retryable failure");

    assertThat(schemaManager.isInitialized(TENANT_A)).isFalse();
    assertThat(schemaManager.attempts).isEqualTo(1);
    assertThat(schemaManager.waits).isZero();
  }

  @Test
  void shouldRetryWhenSqlStateIndicatesRetryableFailure() throws Exception {
    // given
    final var schemaManager =
        new RetryableMigrationSchemaManager(
            1,
            new RuntimeException(
                "liquibase migration failed", new SQLException("transient conflict", "40001", 0)));

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
    assertThat(schemaManager.attempts).isEqualTo(2);
    assertThat(schemaManager.waits).isEqualTo(1);
  }

  @Test
  void shouldRetryWhenSqlErrorCodeIndicatesRetryableFailure() throws Exception {
    // given
    final var schemaManager =
        new RetryableMigrationSchemaManager(
            1,
            new RuntimeException(
                "liquibase migration failed",
                new SQLException("generic db failure", "S0001", 1205)));

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
    assertThat(schemaManager.attempts).isEqualTo(2);
    assertThat(schemaManager.waits).isEqualTo(1);
  }

  // ---- toStableVersion ----

  @Test
  void shouldNormalizeSnapshotVersionToStableBeforeStorage() {
    assertThat(LiquibaseSchemaManager.toStableVersion("8.11.0-SNAPSHOT")).contains("8.11.0");
  }

  @Test
  void shouldReturnEmptyForUnparseableVersion() {
    assertThat(LiquibaseSchemaManager.toStableVersion("development")).isEmpty();
  }

  @Test
  void shouldReturnStableVersionUnchanged() {
    assertThat(LiquibaseSchemaManager.toStableVersion("8.10.0")).contains("8.10.0");
  }

  @Test
  void shouldSkipUpdateSchemaVersionForUnparseableVersion() throws Exception {
    // given - app=development → version cannot be parsed, updateSchemaVersion silently skips and
    // migration still proceeds. TestLiquibaseSchemaManager stubs out the SQL touchpoints so the
    // test simply asserts that startup completes without exception and the tenant is initialized.
    final var configs = Map.of(TENANT_A, autoDdlConfig());
    final var schemaManager = new TestLiquibaseSchemaManager(configs, "development");

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized(TENANT_A)).isTrue();
  }

  // ---- helpers ----

  private static PerTenantSchemaConfig autoDdlConfig() {
    return new PerTenantSchemaConfig(mock(DataSource.class), h2Properties(), "", true, null);
  }

  private static PerTenantSchemaConfig noAutoDdlConfig() {
    return new PerTenantSchemaConfig(mock(DataSource.class), h2Properties(), "", false, null);
  }

  private static PerTenantSchemaConfig configWithPrefix(final String prefix) {
    return new PerTenantSchemaConfig(mock(DataSource.class), h2Properties(), prefix, true, null);
  }

  private static PerTenantSchemaConfig configWithDdlTimeout(final Duration timeout) {
    return new PerTenantSchemaConfig(mock(DataSource.class), h2Properties(), "", true, timeout);
  }

  private static VendorDatabaseProperties h2Properties() {
    final var props = new Properties();
    props.put(VendorDatabaseProperties.DATABASE_ID, "h2");
    props.put("variableValue.previewSize", "8191");
    props.put("userCharColumn.size", "256");
    props.put("errorMessage.size", "4000");
    props.put("treePath.size", "8191");
    props.put("disableFkBeforeTruncate", "false");
    return new VendorDatabaseProperties(props);
  }

  /**
   * Builds a {@link TestLiquibaseSchemaManager} for a single tenant whose {@link
   * LiquibaseSchemaManager#resolveCurrentSchemaVersion} returns {@code schemaVersion}.
   */
  private TestLiquibaseSchemaManager versionCheckManager(
      final String schemaVersion, final String appVersion) {
    final var configs = Map.of(TENANT_A, autoDdlConfig());
    try {
      when(configs.get(TENANT_A).dataSource().getConnection()).thenReturn(mock(Connection.class));
    } catch (final SQLException e) {
      throw new RuntimeException(e);
    }
    return new TestLiquibaseSchemaManager(configs, appVersion) {
      @Override
      protected String resolveCurrentSchemaVersion(
          final Connection connection, final String prefix) {
        return schemaVersion;
      }
    };
  }

  /**
   * Test subclass that overrides {@link #performMigration(PerTenantLiquibase)} to be a no-op so
   * tests can run the full {@code afterPropertiesSet} loop without a real database.
   */
  private static class TestLiquibaseSchemaManager extends LiquibaseSchemaManager {
    TestLiquibaseSchemaManager(
        final Map<String, PerTenantSchemaConfig> configs, final String applicationVersion) {
      super(configs, applicationVersion);
    }

    @Override
    protected PerTenantLiquibase buildPerTenant(
        final String tenantId, final PerTenantSchemaConfig cfg) {
      // Skip building a real SpringLiquibase, but keep the prefix and ddlLockWaitTimeout so
      // helpers behave realistically.
      return new PerTenantLiquibase(
          tenantId, null, cfg.dataSource(), cfg.prefix(), cfg.ddlLockWaitTimeout());
    }

    @Override
    protected void performMigration(final PerTenantLiquibase tenant) throws Exception {
      // no-op
    }

    @Override
    protected void releaseStaleLockIfPresent(final PerTenantLiquibase tenant) {
      // no-op — tested separately by LiquibaseSchemaManagerStaleLockH2Test
    }

    @Override
    protected String resolveCurrentSchemaVersion(final Connection connection, final String prefix) {
      // Default to "fresh DB" so the version check is skipped unless a subclass overrides.
      return null;
    }

    @Override
    protected void updateSchemaVersion(final PerTenantLiquibase tenant) {
      // no-op — H2 test covers the SQL behaviour
    }
  }

  /**
   * Exposes the protected {@link LiquibaseSchemaManager#buildPerTenant} so tests can assert that
   * tenant-scoped facts (prefix, ddlLockWaitTimeout) are propagated into the resulting {@link
   * PerTenantLiquibase}.
   */
  private static final class BuildPerTenantExposingSchemaManager extends LiquibaseSchemaManager {
    BuildPerTenantExposingSchemaManager() {
      super(Map.of(), "8.10.0");
    }

    PerTenantLiquibase expose(final String id, final PerTenantSchemaConfig cfg) {
      return buildPerTenant(id, cfg);
    }
  }

  /**
   * Injects a mock {@link LockService} so stale-lock logic can be unit-tested without a real DB.
   * Overrides {@link #openDatabase} to avoid needing a Liquibase-initialised connection, but
   * deliberately does NOT override {@link #releaseStaleLockIfPresent} — the real implementation is
   * what is under test.
   */
  private static final class MockLockServiceSchemaManager extends LiquibaseSchemaManager {
    private final LockService mockLockService;

    MockLockServiceSchemaManager(final LockService mockLockService) {
      super(Map.of(), "8.10.0");
      this.mockLockService = mockLockService;
    }

    @Override
    protected Database openDatabase(final Connection connection, final String lockTableName)
        throws DatabaseException {
      return mock(Database.class);
    }

    @Override
    protected LockService getLockService(final Database database) {
      return mockLockService;
    }
  }

  /**
   * Retry tests run with a single tenant whose performMigration() fails {@code remainingFailures}
   * times before succeeding.
   */
  private static final class RetryableMigrationSchemaManager extends LiquibaseSchemaManager {
    private int remainingFailures;
    private final RuntimeException failure;
    private int attempts;
    private int waits;

    private RetryableMigrationSchemaManager(
        final int failuresBeforeSuccess, final RuntimeException failure) {
      super(Map.of(TENANT_A, autoDdlConfig()), "8.10.0");
      remainingFailures = failuresBeforeSuccess;
      this.failure = failure;
    }

    @Override
    protected PerTenantLiquibase buildPerTenant(
        final String tenantId, final PerTenantSchemaConfig cfg) {
      return new PerTenantLiquibase(
          tenantId, null, cfg.dataSource(), cfg.prefix(), cfg.ddlLockWaitTimeout());
    }

    @Override
    protected void releaseStaleLockIfPresent(final PerTenantLiquibase tenant) {
      // no-op
    }

    @Override
    protected void checkSchemaVersionCompatibility(final PerTenantLiquibase tenant) {
      // skip
    }

    @Override
    protected void updateSchemaVersion(final PerTenantLiquibase tenant) {
      // no-op
    }

    @Override
    protected void performMigration(final PerTenantLiquibase tenant) {
      attempts++;
      if (remainingFailures-- > 0) {
        throw failure;
      }
    }

    @Override
    protected void waitBeforeRetry(final Duration retryBackoff) {
      waits++;
    }
  }
}
