/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test that verifies the full application can recover from a stale Liquibase lock left
 * by a crashed container. The test pre-seeds a DATABASECHANGELOGLOCK table with a stale lock, then
 * starts the whole Camunda application and asserts that it starts successfully (i.e., the stale
 * lock was automatically detected and released before Liquibase migrations ran).
 */
@Tag("rdbms")
class LiquibaseStaleLockIT {

  /**
   * Unique H2 in-memory database name for this test. A dedicated database is used to avoid
   * interfering with the shared "testdb" used by other RDBMS tests.
   */
  private static final String DB_NAME = "stale-lock-it";

  private static final String H2_URL =
      "jdbc:h2:mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  private static final String LOCK_TABLE = "DATABASECHANGELOGLOCK";
  private static final String SIMULATED_CRASHED_POD = "crashed-pod-10.52.55.196";

  private CamundaRdbmsTestApplication app;

  @AfterEach
  void tearDown() {
    if (app != null) {
      app.close();
    }
  }

  @Test
  void shouldStartApplicationWhenStaleLockIsPresent() throws Exception {
    // given - pre-seed the H2 in-memory database with a stale lock acquired 1 hour ago
    // (exceeds the default 15-minute ddl-lock-wait-timeout threshold)
    insertStaleLock(Instant.now().minus(Duration.ofHours(1)), SIMULATED_CRASHED_POD);

    // when - start the whole application; LiquibaseSchemaManager should detect and release the
    // stale lock (older than the configured ddl-lock-wait-timeout of 15 min) before running
    // migrations
    app =
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withH2()
            // override URL so that the app connects to the same pre-seeded H2 database
            .withUnifiedConfig(c -> c.getData().getSecondaryStorage().getRdbms().setUrl(H2_URL));
    app.start();

    // then - the application started successfully, meaning migrations ran without deadlock
    assertThat(app.isStarted()).isTrue();
  }

  // --- helpers ---

  /**
   * Connects to the H2 in-memory database, creates the DATABASECHANGELOGLOCK table (simulating a
   * previous Liquibase run), and inserts a lock row that appears stale.
   */
  private void insertStaleLock(final Instant lockedSince, final String lockedBy) throws Exception {
    final var dataSource = new JdbcDataSource();
    dataSource.setURL(H2_URL);
    dataSource.setUser("sa");
    dataSource.setPassword("");

    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {

      stmt.execute(
          "CREATE TABLE IF NOT EXISTS "
              + LOCK_TABLE
              + " ("
              + "ID INT NOT NULL, "
              + "LOCKED BOOL NOT NULL, "
              + "LOCKGRANTED TIMESTAMP, "
              + "LOCKEDBY VARCHAR(255), "
              + "CONSTRAINT PK_DATABASECHANGELOGLOCK PRIMARY KEY (ID)"
              + ")");

      // Row ID=1 is the standard Liquibase lock row
      stmt.execute("DELETE FROM " + LOCK_TABLE + " WHERE ID = 1");

      try (final PreparedStatement ps =
          conn.prepareStatement(
              "INSERT INTO "
                  + LOCK_TABLE
                  + " (ID, LOCKED, LOCKGRANTED, LOCKEDBY) VALUES (1, TRUE, ?, ?)")) {
        ps.setTimestamp(1, Timestamp.from(lockedSince));
        ps.setString(2, lockedBy);
        ps.executeUpdate();
      }
    }
    // DB_CLOSE_DELAY=-1 keeps the database alive without an active connection
  }
}
