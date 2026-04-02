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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Date;
import javax.sql.DataSource;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockService;
import org.junit.jupiter.api.Test;

class LiquibaseSchemaManagerTest {

  @Test
  void shouldHaveInitializedFalseByDefault() {
    // given
    final var schemaManager = new LiquibaseSchemaManager();

    // when / then
    assertThat(schemaManager.isInitialized()).isFalse();
  }

  @Test
  void shouldSetInitializedTrueAfterSuccessfulInitialization() throws Exception {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager();

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized()).isTrue();
  }

  @Test
  void shouldKeepInitializedFalseWhenInitializationFails() {
    // given
    final var schemaManager = spy(new TestLiquibaseSchemaManager());
    doThrow(new RuntimeException("Initialization failed")).when(schemaManager).performMigration();

    // when / then
    assertThatThrownBy(() -> schemaManager.afterPropertiesSet())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Initialization failed");

    assertThat(schemaManager.isInitialized()).isFalse();
  }

  @Test
  void shouldSkipStaleLockCheckWhenDdlLockWaitTimeoutIsNull() throws Exception {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager();
    schemaManager.setDdlLockWaitTimeout(null);
    final var mockDataSource = mock(DataSource.class);
    schemaManager.setDataSource(mockDataSource);

    // when
    schemaManager.releaseStaleLockIfPresent();

    // then - no interaction with datasource because timeout is null
    verify(mockDataSource, never()).getConnection();
  }

  @Test
  void shouldSkipStaleLockCheckWhenDataSourceIsNull() throws Exception {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager();
    schemaManager.setDdlLockWaitTimeout(Duration.ofMinutes(10));
    // No datasource set

    // when / then - should not throw any exception
    schemaManager.releaseStaleLockIfPresent();
  }

  @Test
  void shouldNotReleaseLockWhenNoLocksPresent() throws Exception {
    // given
    final var mockLockService = mock(LockService.class);
    when(mockLockService.listLocks()).thenReturn(new DatabaseChangeLogLock[0]);

    final var schemaManager = new TestableSchemaManager(mockLockService);
    schemaManager.setDdlLockWaitTimeout(Duration.ofMinutes(10));

    // when
    schemaManager.releaseStaleLockIfPresent();

    // then
    verify(mockLockService, never()).forceReleaseLock();
  }

  @Test
  void shouldNotReleaseLockWhenLockIsRecent() throws Exception {
    // given
    final var recentLock = new DatabaseChangeLogLock(1, new Date(), "some-host");
    final var mockLockService = mock(LockService.class);
    when(mockLockService.listLocks()).thenReturn(new DatabaseChangeLogLock[] {recentLock});

    final var schemaManager = new TestableSchemaManager(mockLockService);
    schemaManager.setDdlLockWaitTimeout(Duration.ofMinutes(10));

    // when
    schemaManager.releaseStaleLockIfPresent();

    // then - lock is recent (just now), so it should not be released
    verify(mockLockService, never()).forceReleaseLock();
  }

  @Test
  void shouldReleaseLockWhenLockIsStale() throws Exception {
    // given
    final var staleLockTime = new Date(System.currentTimeMillis() - Duration.ofHours(1).toMillis());
    final var staleLock = new DatabaseChangeLogLock(1, staleLockTime, "crashed-pod");
    final var mockLockService = mock(LockService.class);
    when(mockLockService.listLocks()).thenReturn(new DatabaseChangeLogLock[] {staleLock});

    final var schemaManager = new TestableSchemaManager(mockLockService);
    schemaManager.setDdlLockWaitTimeout(Duration.ofMinutes(10));

    // when
    schemaManager.releaseStaleLockIfPresent();

    // then - stale lock (1 hour old, timeout 10 min) should be released
    verify(mockLockService).forceReleaseLock();
  }

  @Test
  void shouldContinueMigrationWhenStaleLockCheckThrowsException() throws Exception {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager();
    schemaManager.setDdlLockWaitTimeout(Duration.ofMinutes(10));
    final var mockDataSource = mock(DataSource.class);
    when(mockDataSource.getConnection()).thenThrow(new RuntimeException("DB connection failed"));
    schemaManager.setDataSource(mockDataSource);

    // when / then - exception should be swallowed, method should return normally
    schemaManager.releaseStaleLockIfPresent();
  }

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
    assertThatThrownBy(schemaManager::afterPropertiesSet)
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
    assertThatThrownBy(schemaManager::afterPropertiesSet)
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
    schemaManager.afterPropertiesSet();

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
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized()).isTrue();
    assertThat(schemaManager.attempts).isEqualTo(2);
    assertThat(schemaManager.waits).isEqualTo(1);
  }

  /**
   * Test implementation that overrides the parent's afterPropertiesSet to avoid actual Liquibase
   * initialization. Designed for extension by test subclasses (e.g. {@code TestableSchemaManager}).
   */
  private static class TestLiquibaseSchemaManager extends LiquibaseSchemaManager {
    @Override
    public void afterPropertiesSet() throws Exception {
      // Skip the actual Liquibase initialization (super.afterPropertiesSet())
      // and just perform our state update
      releaseStaleLockIfPresent();
      performMigration();
      setInitialized();
    }

    @Override
    protected void performMigration() {
      // No-op for testing, can be overridden in spy
    }

    protected void setInitialized() {
      // Access the inherited behavior through a test method
      try {
        final var field = LiquibaseSchemaManager.class.getDeclaredField("initialized");
        field.setAccessible(true);
        field.set(this, true);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Test implementation that injects a mock {@link LockService} and a mock {@link DataSource} to
   * allow testing of stale lock detection without a real database.
   */
  private static final class TestableSchemaManager extends TestLiquibaseSchemaManager {
    private final LockService mockLockService;

    TestableSchemaManager(final LockService mockLockService) {
      this.mockLockService = mockLockService;
      final var mockDataSource = mock(DataSource.class);
      final var mockConnection = mock(Connection.class);
      try {
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
      setDataSource(mockDataSource);
    }

    @Override
    protected Database openDatabase(final Connection connection) throws DatabaseException {
      return mock(Database.class);
    }

    @Override
    protected LockService getLockService(final Database database) {
      return mockLockService;
    }
  }

  private static final class RetryableMigrationSchemaManager extends LiquibaseSchemaManager {
    private int remainingFailures;
    private final RuntimeException failure;
    private int attempts;
    private int waits;

    private RetryableMigrationSchemaManager(
        final int failuresBeforeSuccess, final RuntimeException failure) {
      remainingFailures = failuresBeforeSuccess;
      this.failure = failure;
    }

    @Override
    protected void performMigration() {
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
