/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * H2-based test that verifies stale Liquibase lock detection and release against a real in-memory
 * database via {@link LiquibaseSchemaManager}.
 */
class LiquibaseSchemaManagerStaleLockH2Test {

  private static final String DB_URL = "jdbc:h2:mem:liquibase-lock-test;DB_CLOSE_DELAY=-1";
  private static final String LOCK_TABLE = "DATABASECHANGELOGLOCK";

  private JdbcDataSource dataSource;

  /**
   * The instant the schema manager under test reads as "now", so probe spacing can be asserted
   * without waiting out a {@code ddl-lock-wait-timeout}.
   */
  private Instant clockNow;

  @BeforeEach
  void setUp() throws Exception {
    clockNow = Instant.now();
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
    schemaManager.initialize();

    // then - migration completed successfully and the stale lock was released
    assertThat(isLockHeld()).isFalse();
  }

  @Test
  void shouldNotReleaseRecentLockWhenTimeoutNotExceeded() throws Exception {
    // given - insert a recent lock (just acquired)
    insertLock(Instant.now(), "another-running-pod");

    // when - releaseStaleLockIfPresent with a 10-minute timeout (lock is fresh, must not be
    // released)
    final var schemaManager = buildSchemaManager(Duration.ofMinutes(10));
    schemaManager.releaseStaleLockIfPresent();

    // then - the recent lock remains held
    assertThat(isLockHeld()).isTrue();
  }

  @Test
  void shouldNotProbeAgainWithinTheTimeoutSoALivePeersLockSurvives() throws Exception {
    // given - a peer holding the lock for longer than the timeout, which is indistinguishable
    // from a crashed one; the first probe releases it, as it always has
    final var schemaManager = buildSchemaManager(Duration.ofMinutes(10));
    insertLock(Instant.now().minus(Duration.ofHours(1)), "peer-still-migrating");
    schemaManager.releaseStaleLockIfPresent();
    assertThat(isLockHeld()).isFalse();

    // when - the peer reacquires it and this tenant's retry loop comes round again straight away
    insertLock(Instant.now().minus(Duration.ofHours(1)), "peer-still-migrating");
    schemaManager.releaseStaleLockIfPresent();

    // then - the probe is spaced, so the peer keeps its lock instead of losing it every few
    // seconds to a second changelog run against the same schema
    assertThat(isLockHeld()).isTrue();
  }

  @Test
  void shouldProbeAgainAsSoonAsAnObservedLockCouldHaveBecomeStale() throws Exception {
    // given - a peer's lock a minute short of the ten-minute staleness threshold, so the first
    // probe reads the lock table and correctly leaves it alone
    final var schemaManager = buildSchemaManager(Duration.ofMinutes(10));
    insertLock(clockNow.minus(Duration.ofMinutes(9)), "peer-about-to-crash");
    schemaManager.releaseStaleLockIfPresent();
    assertThat(isLockHeld()).isTrue();

    // when - that peer dies and the retry loop comes round just after its lock turned stale
    advanceClock(Duration.ofMinutes(1).plusSeconds(1));
    schemaManager.releaseStaleLockIfPresent();

    // then - the lock is released now rather than a further ddl-lock-wait-timeout later: spacing
    // the next probe from the last read would double the outage a crashed peer causes
    assertThat(isLockHeld()).isFalse();
  }

  @Test
  void shouldProbeAgainAfterOneThatCouldNotReachTheDatabase() throws Exception {
    // given - a crashed peer's stale lock, and a database that is unreachable when the tenant's
    // first attempt probes for it
    insertLock(Instant.now().minus(Duration.ofHours(1)), "crashed-pod");
    final var unreachableAtFirst = mock(DataSource.class);
    when(unreachableAtFirst.getConnection())
        .thenThrow(new SQLException("connection refused"))
        .thenAnswer(invocation -> dataSource.getConnection());
    final var schemaManager =
        new LiquibaseSchemaManager(
            new PerTenantSchemaConfig(
                unreachableAtFirst,
                VendorDatabasePropertiesLoader.load("h2"),
                "",
                true,
                Duration.ofMinutes(10)),
            "8.10.0");

    // when - that probe fails, and the retry loop comes round again once the database is back
    schemaManager.releaseStaleLockIfPresent();
    schemaManager.releaseStaleLockIfPresent();

    // then - the failed probe released nothing, so it does not space out the one that unblocks
    // the tenant; counting it would leave the lock held for the whole ddl-lock-wait-timeout
    assertThat(isLockHeld()).isFalse();
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

  private LiquibaseSchemaManager buildSchemaManager(final Duration ddlLockWaitTimeout)
      throws Exception {
    return new LiquibaseSchemaManager(
        configFor(ddlLockWaitTimeout),
        "8.10.0",
        new RdbmsSchemaVersionStore(dataSource, "", "8.10.0"),
        () -> clockNow);
  }

  private void advanceClock(final Duration by) {
    clockNow = clockNow.plus(by);
  }

  private PerTenantSchemaConfig configFor(final Duration ddlLockWaitTimeout) throws Exception {
    return new PerTenantSchemaConfig(
        dataSource, VendorDatabasePropertiesLoader.load("h2"), "", true, ddlLockWaitTimeout);
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
