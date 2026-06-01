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

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockService;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the per-physical-tenant {@link LiquibaseSchemaManager}. Schema-version behaviour
 * is covered by {@link RdbmsSchemaVersionStoreTest}; multi-tenant orchestration by {@link
 * DefaultRdbmsSchemaManagerRegistryTest}.
 */
class LiquibaseSchemaManagerTest {

  // ---- initialize / isInitialized ----

  @Test
  void shouldMarkInitializedAfterMigration() throws Exception {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager(autoDdlConfig(), "8.10.0");

    // when
    schemaManager.initialize();

    // then
    assertThat(schemaManager.isInitialized()).isTrue();
  }

  @Test
  void shouldNotBeInitializedBeforeMigration() {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager(autoDdlConfig(), "8.10.0");

    // then
    assertThat(schemaManager.isInitialized()).isFalse();
  }

  @Test
  void shouldAbortStartupWhenApplicationVersionIsNull() {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager(autoDdlConfig(), null);

    // when / then
    assertThatThrownBy(schemaManager::initialize)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("applicationVersion is not configured");
  }

  @Test
  void shouldRunVersionCheckThenMigrationThenRecordVersion() throws Exception {
    // given
    final var versionStore = mock(RdbmsSchemaVersionStore.class);
    final var schemaManager =
        new TestLiquibaseSchemaManager(autoDdlConfig(), "8.10.0", versionStore);

    // when
    schemaManager.initialize();

    // then - the version compatibility check and the version recording both ran
    verify(versionStore).checkCompatibility();
    verify(versionStore).recordCurrentVersion();
    assertThat(schemaManager.isInitialized()).isTrue();
  }

  // ---- stale lock (mock-based) ----

  @Test
  void shouldSkipStaleLockCheckWhenDataSourceIsNull() {
    // given - ddl timeout is set but dataSource is null
    final var schemaManager =
        new LiquibaseSchemaManager(
            new PerTenantSchemaConfig(null, h2Properties(), "", true, Duration.ofMinutes(10)),
            "8.10.0");

    // when / then - should not throw when dataSource is null
    schemaManager.releaseStaleLockIfPresent();
  }

  @Test
  void shouldNotReleaseLockWhenNoLocksPresent() throws Exception {
    // given
    final var mockLockService = mock(LockService.class);
    when(mockLockService.listLocks()).thenReturn(new DatabaseChangeLogLock[0]);

    final var mockDataSource = mock(DataSource.class);
    when(mockDataSource.getConnection()).thenReturn(mock(Connection.class));
    final var schemaManager =
        new MockLockServiceSchemaManager(
            new PerTenantSchemaConfig(
                mockDataSource, h2Properties(), "", true, Duration.ofMinutes(10)),
            mockLockService);

    // when
    schemaManager.releaseStaleLockIfPresent();

    // then - empty lock list → no release
    verify(mockLockService, never()).forceReleaseLock();
  }

  @Test
  void shouldContinueMigrationWhenStaleLockCheckThrowsException() throws Exception {
    // given - getConnection() throws inside releaseStaleLockIfPresent
    final var mockDataSource = mock(DataSource.class);
    when(mockDataSource.getConnection()).thenThrow(new RuntimeException("DB connection failed"));
    final var schemaManager =
        new LiquibaseSchemaManager(
            new PerTenantSchemaConfig(
                mockDataSource, h2Properties(), "", true, Duration.ofMinutes(10)),
            "8.10.0");

    // when / then - exception should be swallowed, method should return normally
    schemaManager.releaseStaleLockIfPresent();
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
    schemaManager.initialize();

    // then
    assertThat(schemaManager.isInitialized()).isTrue();
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
    assertThatThrownBy(schemaManager::initialize)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("liquibase migration failed");

    assertThat(schemaManager.isInitialized()).isFalse();
    assertThat(schemaManager.attempts).isEqualTo(3);
    assertThat(schemaManager.waits).isEqualTo(2);
  }

  @Test
  void shouldNotRetryWhenMigrationFailureIsNotDeadlock() {
    // given
    final var schemaManager =
        new RetryableMigrationSchemaManager(1, new RuntimeException("non-retryable failure"));

    // when / then
    assertThatThrownBy(schemaManager::initialize)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("non-retryable failure");

    assertThat(schemaManager.isInitialized()).isFalse();
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
    schemaManager.initialize();

    // then
    assertThat(schemaManager.isInitialized()).isTrue();
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
    schemaManager.initialize();

    // then
    assertThat(schemaManager.isInitialized()).isTrue();
    assertThat(schemaManager.attempts).isEqualTo(2);
    assertThat(schemaManager.waits).isEqualTo(1);
  }

  // ---- helpers ----

  private static PerTenantSchemaConfig autoDdlConfig() {
    return new PerTenantSchemaConfig(mock(DataSource.class), h2Properties(), "", true, null);
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
   * Test subclass that skips the real {@link SpringLiquibase} runner, stale-lock handling, and
   * migration so tests can drive {@link #initialize()} without a real database. Schema-version
   * interactions are delegated to a (mocked) {@link RdbmsSchemaVersionStore}.
   */
  private static class TestLiquibaseSchemaManager extends LiquibaseSchemaManager {
    TestLiquibaseSchemaManager(
        final PerTenantSchemaConfig config, final String applicationVersion) {
      this(config, applicationVersion, mock(RdbmsSchemaVersionStore.class));
    }

    TestLiquibaseSchemaManager(
        final PerTenantSchemaConfig config,
        final String applicationVersion,
        final RdbmsSchemaVersionStore versionStore) {
      super(config, applicationVersion, versionStore);
    }

    @Override
    protected SpringLiquibase buildRunner() {
      return null;
    }

    @Override
    protected void releaseStaleLockIfPresent() {
      // no-op — tested separately
    }

    @Override
    protected void performMigration(final SpringLiquibase runner) {
      // no-op
    }
  }

  /**
   * Injects a mock {@link LockService} so stale-lock logic can be unit-tested without a real DB.
   * Deliberately does NOT override {@link #releaseStaleLockIfPresent()} — the real implementation
   * is what is under test.
   */
  private static final class MockLockServiceSchemaManager extends LiquibaseSchemaManager {
    private final LockService mockLockService;

    MockLockServiceSchemaManager(
        final PerTenantSchemaConfig config, final LockService mockLockService) {
      super(config, "8.10.0");
      this.mockLockService = mockLockService;
    }

    @Override
    protected liquibase.database.Database openDatabase(
        final Connection connection, final String lockTableName) {
      return mock(liquibase.database.Database.class);
    }

    @Override
    protected LockService getLockService(final liquibase.database.Database database) {
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
      super(autoDdlConfig(), "8.10.0", mock(RdbmsSchemaVersionStore.class));
      remainingFailures = failuresBeforeSuccess;
      this.failure = failure;
    }

    @Override
    protected SpringLiquibase buildRunner() {
      return null;
    }

    @Override
    protected void releaseStaleLockIfPresent() {
      // no-op
    }

    @Override
    protected void performMigration(final SpringLiquibase runner) {
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
