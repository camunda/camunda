/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * H2-based test that verifies stale Liquibase lock detection and release against a real in-memory
 * database.
 */
class LiquibaseSchemaManagerStaleLockH2Test {

  private static final String DB_URL = "jdbc:h2:mem:liquibase-lock-test;DB_CLOSE_DELAY=-1";
  private static final String LOCK_TABLE = "DATABASECHANGELOGLOCK";

  private JdbcDataSource dataSource;

  @BeforeEach
  void setUp() throws Exception {
    dataSource = new JdbcDataSource();
    dataSource.setURL(DB_URL + ";MODE=LEGACY");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    // Clean up any prior state from a previous test run
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS " + LOCK_TABLE);
    }
    createLockTable();
  }

  @Test
  void shouldReleaseStaleLockAndRunMigrations() throws Exception {
    // given - insert a stale lock (granted 1 hour ago)
    insertLock(Instant.now().minus(Duration.ofHours(1)), "crashed-pod-192.168.1.1");

    // when - run LiquibaseSchemaManager with a 10-minute timeout (so the 1-hour-old lock is stale)
    final var schemaManager = buildSchemaManager(Duration.ofMinutes(10));
    schemaManager.afterPropertiesSet();

    // then - migration completed successfully and the stale lock was released
    assertThat(schemaManager.isInitialized()).isTrue();
    assertThat(isLockHeld()).isFalse();
  }

  @Test
  void shouldNotReleaseRecentLockWhenTimeoutNotExceeded() throws Exception {
    // given - insert a recent lock (just acquired)
    insertLock(Instant.now(), "another-running-pod");

    // when - configure the schema manager with a 10-minute timeout
    // Since the lock is brand-new, it should NOT be force-released
    final var schemaManager = buildSchemaManager(Duration.ofMinutes(10));

    // then - the stale-lock check should not release the recent lock
    schemaManager.releaseStaleLockIfPresent();

    // the recent lock remains held
    assertThat(isLockHeld()).isTrue();
  }

  @Test
  void shouldSkipLockCheckWhenTimeoutIsNull() throws Exception {
    // given - insert a stale lock
    insertLock(Instant.now().minus(Duration.ofHours(1)), "crashed-pod");

    // when - timeout is null (feature disabled)
    final var schemaManager = buildSchemaManager(null);
    schemaManager.releaseStaleLockIfPresent();

    // then - the stale lock should still be held (not released because timeout is disabled)
    assertThat(isLockHeld()).isTrue();
  }

  // --- helpers ---

  private LiquibaseSchemaManager buildSchemaManager(final Duration ddlLockWaitTimeout) {
    final var manager = new LiquibaseSchemaManager();
    manager.setDataSource(dataSource);
    manager.setApplicationVersion("8.10.0");
    manager.setDatabaseChangeLogLockTable(LOCK_TABLE);
    manager.setChangeLog("db/changelog/rdbms-exporter/changelog-master.xml");
    manager.setParameters(
        Map.of(
            "prefix", "",
            "userCharColumnSize", "256",
            "errorMessageSize", "4000",
            "treePathSize", "8191"));
    manager.setDdlLockWaitTimeout(ddlLockWaitTimeout);
    return manager;
  }

  /**
   * Creates a minimal DATABASECHANGELOGLOCK table matching the schema expected by Liquibase. This
   * simulates the state of a database where Liquibase has run previously.
   */
  private void createLockTable() throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE "
              + LOCK_TABLE
              + " ("
              + "ID INT NOT NULL, "
              + "LOCKED BOOL NOT NULL, "
              + "LOCKGRANTED TIMESTAMP, "
              + "LOCKEDBY VARCHAR(255), "
              + "CONSTRAINT PK_DATABASECHANGELOGLOCK PRIMARY KEY (ID)"
              + ")");
      // Liquibase expects a row with ID=1 to exist (it inserts it on first run)
      stmt.execute("INSERT INTO " + LOCK_TABLE + " (ID, LOCKED) VALUES (1, FALSE)");
    }
  }

  /** Inserts (or updates) the lock row to simulate a lock held since the given time. */
  private void insertLock(final Instant lockedSince, final String lockedBy) throws Exception {
    try (final var conn = dataSource.getConnection();
        final PreparedStatement ps =
            conn.prepareStatement(
                "UPDATE "
                    + LOCK_TABLE
                    + " SET LOCKED = TRUE, LOCKGRANTED = ?, LOCKEDBY = ? WHERE ID = 1")) {
      ps.setTimestamp(1, Timestamp.from(lockedSince));
      ps.setString(2, lockedBy);
      ps.executeUpdate();
    }
  }

  /** Returns true if the lock table has an active lock. */
  private boolean isLockHeld() throws Exception {
    try (final Connection conn = dataSource.getConnection();
        final var ps =
            conn.prepareStatement("SELECT LOCKED FROM " + LOCK_TABLE + " WHERE ID = 1")) {
      final var rs = ps.executeQuery();
      return rs.next() && rs.getBoolean("LOCKED");
    }
  }
}
