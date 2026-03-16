/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the database schema using Liquibase for multi-tenant applications.
 *
 * <p>This class extends {@link MultiTenantSpringLiquibase} to leverage its capabilities for
 * managing database migrations in a multi-tenant environment. It also implements the {@link
 * RdbmsSchemaManager} interface to provide a method for checking if the schema has been
 * initialized.
 */
public class LiquibaseSchemaManager extends MultiTenantSpringLiquibase
    implements RdbmsSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(LiquibaseSchemaManager.class);

  private volatile boolean initialized = false;
  private Duration ddlLockWaitTimeout;

  @Override
  public void afterPropertiesSet() throws Exception {
    releaseStaleLockIfPresent();
    super.afterPropertiesSet();
    initialized = true;
    LOG.debug("Liquibase migrations completed.");
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  public Duration getDdlLockWaitTimeout() {
    return ddlLockWaitTimeout;
  }

  public void setDdlLockWaitTimeout(final Duration ddlLockWaitTimeout) {
    this.ddlLockWaitTimeout = ddlLockWaitTimeout;
  }

  /**
   * Checks for stale Liquibase locks and forcibly releases them if they are older than the
   * configured {@link #ddlLockWaitTimeout}. This allows recovery from container crashes that left
   * the schema locked without being properly cleaned up.
   *
   * <p>If {@link #ddlLockWaitTimeout} is {@code null}, or the lock table does not exist yet (first
   * run), this method does nothing.
   */
  protected void releaseStaleLockIfPresent() {
    if (ddlLockWaitTimeout == null || getDataSource() == null) {
      return;
    }
    try (final var connection = getDataSource().getConnection()) {
      final var database = openDatabase(connection);
      try {
        final var lockService = getLockService(database);
        final var threshold = Instant.now().minus(ddlLockWaitTimeout);
        for (final var lock : lockService.listLocks()) {
          if (lock.getLockGranted() != null
              && lock.getLockGranted().toInstant().isBefore(threshold)) {
            LOG.warn(
                "Detected stale Liquibase lock acquired at {} by '{}' (older than configured"
                    + " ddl-lock-wait-timeout of {}). Releasing lock to allow migrations to"
                    + " proceed.",
                lock.getLockGranted(),
                lock.getLockedBy(),
                ddlLockWaitTimeout);
            lockService.forceReleaseLock();
            LOG.info("Stale Liquibase lock released successfully.");
            break;
          }
        }
      } finally {
        database.close();
      }
    } catch (final Exception e) {
      LOG.warn("Failed to check or release stale Liquibase lock. Proceeding with migration.", e);
    }
  }

  /**
   * Creates a Liquibase {@link Database} from the given JDBC connection. Protected to allow
   * overriding in tests.
   */
  protected Database openDatabase(final Connection connection) throws DatabaseException {
    final var database =
        DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
    final var lockTableName = getDatabaseChangeLogLockTable();
    if (lockTableName != null) {
      database.setDatabaseChangeLogLockTableName(lockTableName);
    }
    return database;
  }

  /**
   * Returns the {@link LockService} for the given database. Protected to allow overriding in tests.
   */
  protected LockService getLockService(final Database database) {
    return LockServiceFactory.getInstance().getLockService(database);
  }
}
