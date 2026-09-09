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

import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import liquibase.integration.spring.SpringLiquibase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * H2-based integration tests for the RDBMS schema version compatibility check.
 *
 * <p>These tests run Liquibase against a real in-memory H2 database for a single schema to validate
 * the version enforcement logic in {@link LiquibaseSchemaManager} and {@link
 * RdbmsSchemaVersionStore}.
 */
class LiquibaseSchemaManagerVersionCheckH2Test {

  private static final String DB_URL = "jdbc:h2:mem:version-check-test;DB_CLOSE_DELAY=-1";
  private static final String PRE_8_10_CHANGE_LOG =
      "db/changelog/rdbms-exporter-pre-8-10/changelog-master.xml";

  private JdbcDataSource dataSource;

  @BeforeEach
  void setUp() throws Exception {
    dataSource = new JdbcDataSource();
    dataSource.setURL(DB_URL);
    dataSource.setUser("sa");
    dataSource.setPassword("");

    // Drop all schema objects from previous test runs so each test starts clean.
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
  }

  // ---- Acceptance criterion 1: Allowed patch upgrade ----

  @Test
  void shouldAllowPatchUpgradeWhenSchemaVersionIsOlderPatchOfSameMinor() throws Exception {
    // given: existing DB with RDBMS_SCHEMA_VERSION = '8.9.0'
    runLiquibaseWithVersion("8.9.0");

    // when: start app with 8.9.5 (patch upgrade)
    final var manager = buildSchemaManager("8.9.5");
    manager.initialize();

    // then: the migration completed and recorded the running version
    assertThat(readSchemaVersion()).isEqualTo("8.9.5");
  }

  // ---- Acceptance criterion 2: Allowed minor upgrade ----

  @Test
  void shouldAllowMinorUpgradeWhenSchemaVersionIsNextMinor() throws Exception {
    // given: existing DB with RDBMS_SCHEMA_VERSION = '8.9.1'
    runLiquibaseWithVersion("8.9.1");

    // when: start app with 8.10.0 (minor upgrade)
    final var manager = buildSchemaManager("8.10.0");
    manager.initialize();

    // then: the migration completed and recorded the running version
    assertThat(readSchemaVersion()).isEqualTo("8.10.0");
  }

  // ---- Acceptance criterion 3: Illegal skipped minor ----

  @Test
  void shouldRejectSkippedMinorVersionAndNotRunLiquibase() throws Exception {
    // given: existing DB with RDBMS_SCHEMA_VERSION = '8.9.0'
    runLiquibaseWithVersion("8.9.0");
    final int changelogRowsBefore = countChangelogRows();

    // when: start app with 8.11.0 (skips 8.10)
    final var manager = buildSchemaManager("8.11.0");

    // then: startup fails with a clear error
    assertThatThrownBy(manager::initialize)
        .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class)
        .hasMessageContaining("8.9.0")
        .hasMessageContaining("8.11.0");
    // Schema version should NOT have been updated
    assertThat(readSchemaVersion()).isEqualTo("8.9.0");
    // Liquibase should NOT have applied any new changesets
    assertThat(countChangelogRows()).isEqualTo(changelogRowsBefore);
  }

  // ---- Acceptance criterion 4: Fresh DB ----

  @Test
  void shouldCreateSchemaAndInsertVersionForFreshDatabase() throws Exception {
    // given: completely fresh database (no tables at all)

    // when: start app with some version
    final var manager = buildSchemaManager("8.10.0");
    manager.initialize();

    // then: schema is created and the version is recorded
    assertThat(readSchemaVersion()).isEqualTo("8.10.0");
  }

  // ---- Acceptance criterion 5: Feature introduction in version 8.10 ----

  @Test
  void shouldSeedPreVersioningSchemaVersionWhenNoVersionTableExists() throws Exception {
    // given: a pre-8.10 database that pre-8.10 code finished, and so has no RDBMS_SCHEMA_VERSION
    createCompletedPreVersioningSchema();

    // when: start app with 8.10.0 (valid upgrade from the seeded 8.9.0)
    final var manager = buildSchemaManager("8.10.0");
    manager.initialize();

    // then: the migration completed and recorded the running version
    assertThat(readSchemaVersion()).isEqualTo("8.10.0");
  }

  @Test
  void shouldRejectUpgradeFromPreVersioningDatabaseWhenMinorVersionIsSkipped() throws Exception {
    // given: a pre-8.10 database that pre-8.10 code finished
    createCompletedPreVersioningSchema();

    // when: start app with 8.11.0 (seeded schema=8.9.0, skips 8.10)
    final var manager = buildSchemaManager("8.11.0");

    // then: startup fails
    assertThatThrownBy(manager::initialize)
        .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class)
        .hasMessageContaining("8.9.0")
        .hasMessageContaining("8.11.0");
  }

  // ---- #62554: a schema being created must never read as a pre-versioning one ----

  @Test
  void shouldRecordSchemaVersionBeforeCreatingAnyMigratedTable() throws Exception {
    // given: a completely fresh database

    // when: only the schema-version seed step has run, which is the earliest point at which any
    // node — this one or a peer starting alongside it — can observe this schema
    buildSchemaManager("8.11.0").seedSchemaVersion();

    // then: the version table is already there, and none of the migrated tables are. A peer can
    // therefore never see EXPORTER_POSITION without RDBMS_SCHEMA_VERSION, which is the shape it
    // used to mistake for a pre-versioning 8.9.x database.
    assertThat(tableExists("RDBMS_SCHEMA_VERSION")).isTrue();
    assertThat(tableExists("EXPORTER_POSITION")).isFalse();
    // and no version is claimed for a schema that has not been migrated yet
    assertThat(readSchemaVersion()).isNull();
  }

  @Test
  void shouldNotSeedPreVersioningVersionWhenSchemaIsAlreadyPast8_10() throws Exception {
    // given: a schema migrated to 8.10 but carrying no recorded version — a database from a build
    // that predates the seed run, whose version write did not land
    runMasterChangelogOnly();
    assertThat(readSchemaVersion()).isNull();

    // when: start app with 8.11.0
    buildSchemaManager("8.11.0").initialize();

    // then: the tables 8.10 creates are positive evidence that this is not a pre-versioning
    // schema, so nothing is seeded and the upgrade is not refused as a skipped minor
    assertThat(readSchemaVersion()).isEqualTo("8.11.0");
  }

  @Test
  void shouldNotSeedPreVersioningVersionWhenAFirstMigrationWasInterrupted() throws Exception {
    // given: a first migration by a build without the seed run that created EXPORTER_POSITION and
    // then died, so none of the later pre-8.10 objects exist
    createExporterPositionTable();
    assertThat(indexExists("IDX_AUDIT_LOG_ACTOR_ID")).isFalse();

    // when: that half-created schema is picked up by 8.11.0, two minors on
    buildSchemaManager("8.11.0").initialize();

    // then: an unfinished migration is not a legacy database, so nothing is seeded and the
    // migration is resumed rather than refused as an upgrade that skips a minor
    assertThat(readSchemaVersion()).isEqualTo("8.11.0");
  }

  @Test
  void shouldNotSeedAgainWhenAVersionRowIsAlreadyPresent() throws Exception {
    // given: a pre-versioning schema whose seed insert committed but whose DATABASECHANGELOG row
    // did not. Liquibase commits a changeset's changes before it records the changeset, so a crash
    // in between leaves exactly this state, and the next attempt re-evaluates the seed changeset.
    createCompletedPreVersioningSchema();
    createSchemaVersionTableWithVersion("8.9.0");

    // when: start app with 8.10.0 (valid upgrade from the seeded 8.9.0)
    buildSchemaManager("8.10.0").initialize();

    // then: the insert was skipped rather than repeated into a primary-key violation
    assertThat(readSchemaVersion()).isEqualTo("8.10.0");
  }

  // ---- helpers ----

  private LiquibaseSchemaManager buildSchemaManager(final String appVersion) throws Exception {
    return new LiquibaseSchemaManager(schemaConfig(), appVersion);
  }

  private PerTenantSchemaConfig schemaConfig() throws Exception {
    return new PerTenantSchemaConfig(
        dataSource, VendorDatabasePropertiesLoader.load("h2"), "", true, null);
  }

  /**
   * Initializes the schema manager with the given application version by running all Liquibase
   * migrations and recording the version in {@code RDBMS_SCHEMA_VERSION}.
   */
  private void runLiquibaseWithVersion(final String version) throws Exception {
    buildSchemaManager(version).initialize();
  }

  /**
   * Creates a minimal {@code EXPORTER_POSITION} table to simulate a pre-8.10 database that has not
   * yet had the {@code RDBMS_SCHEMA_VERSION} table applied.
   */
  private void createExporterPositionTable() throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS EXPORTER_POSITION ("
              + "PARTITION_ID NUMBER NOT NULL, "
              + "EXPORTER VARCHAR(200), "
              + "LAST_EXPORTED_POSITION BIGINT, "
              + "CREATED TIMESTAMP, "
              + "LAST_UPDATED TIMESTAMP, "
              + "CONSTRAINT PK_EXPORTER_POSITION PRIMARY KEY (PARTITION_ID)"
              + ")");
    }
  }

  /**
   * Applies the real 8.9 changesets and nothing later, which is what pre-8.10 code would have left
   * behind: a complete schema with no {@code RDBMS_SCHEMA_VERSION} table.
   */
  private void createCompletedPreVersioningSchema() throws Exception {
    final var manager =
        new LiquibaseSchemaManager(schemaConfig(), "8.9.9") {
          @Override
          protected SpringLiquibase buildRunner() {
            final var runner = super.buildRunner();
            runner.setChangeLog(PRE_8_10_CHANGE_LOG);
            return runner;
          }
        };
    manager.performMigration(manager.buildRunner());
  }

  /** Simulates a seed insert that committed without its changeset being recorded. */
  private void createSchemaVersionTableWithVersion(final String version) throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS RDBMS_SCHEMA_VERSION ("
              + "ID SMALLINT DEFAULT 1 NOT NULL PRIMARY KEY, VERSION VARCHAR(32) NOT NULL)");
      stmt.execute("INSERT INTO RDBMS_SCHEMA_VERSION (ID, VERSION) VALUES (1, '" + version + "')");
    }
  }

  /**
   * Applies the master changelog on its own, without the schema-version seed run and without
   * recording a version — what a build from before #62554 left behind if its version write failed.
   */
  private void runMasterChangelogOnly() throws Exception {
    final var manager = buildSchemaManager("8.10.0");
    manager.performMigration(manager.buildRunner());
  }

  private boolean tableExists(final String tableName) throws Exception {
    try (final var conn = dataSource.getConnection();
        final var rs =
            conn.getMetaData()
                .getTables(
                    conn.getCatalog(), conn.getSchema(), tableName, new String[] {"TABLE"})) {
      return rs.next();
    }
  }

  private boolean indexExists(final String indexName) throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt =
            conn.prepareStatement(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = ?")) {
      stmt.setString(1, indexName);
      final var rs = stmt.executeQuery();
      return rs.next() && rs.getInt(1) > 0;
    }
  }

  private String readSchemaVersion() throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.prepareStatement("SELECT VERSION FROM RDBMS_SCHEMA_VERSION")) {
      stmt.setMaxRows(1);
      final var rs = stmt.executeQuery();
      return rs.next() ? rs.getString(1) : null;
    }
  }

  private int countChangelogRows() throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement();
        final var rs = stmt.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }
}
