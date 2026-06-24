/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

/**
 * Validates that {@link RollingUpgradeCompatibilityValidator} correctly identifies safe and unsafe
 * schema changes for rolling-upgrade compatibility.
 *
 * <p>Each test loads a small fixture Liquibase changelog and asserts whether the validator produces
 * violations. Tests are named after the acceptance criteria in the issue.
 */
class RollingUpgradeCompatibilityValidatorTest {

  private static final String FIXTURE_BASE = "rolling-upgrade-fixtures/";

  // ---------------------------------------------------------------------------
  // Happy-path tests: safe additive operations must pass validation
  // ---------------------------------------------------------------------------

  @Test
  void shouldAllowCreateTable() throws Exception {
    // given – a standalone createTable changeset
    final var changeSets = loadChangeSets("create-table.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations).as("createTable must pass validation").isEmpty();
  }

  @Test
  void shouldAllowAddNullableColumn() throws Exception {
    // given – an addColumn changeset with a nullable column
    final var changeSets = loadChangeSets("add-nullable-column.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations).as("addColumn with nullable column must pass validation").isEmpty();
  }

  @Test
  void shouldAllowCreateIndex() throws Exception {
    // given – a standalone createIndex changeset
    final var changeSets = loadChangeSets("create-index.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations).as("createIndex must pass validation").isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Rejection tests: unsafe operations must produce violations
  // ---------------------------------------------------------------------------

  @Test
  void shouldRejectDropColumn() throws Exception {
    // given
    final var changeSets = loadChangeSets("drop-column.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations)
        .as("drop column must be rejected")
        .hasSize(1)
        .allSatisfy(v -> assertThat(v).contains("drop_existing_column"))
        .allSatisfy(v -> assertThat(v).containsIgnoringCase("dropping column"));
  }

  @Test
  void shouldRejectRenameColumn() throws Exception {
    // given
    final var changeSets = loadChangeSets("rename-column.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations)
        .as("rename column must be rejected")
        .hasSize(1)
        .allSatisfy(v -> assertThat(v).contains("rename_existing_column"))
        .allSatisfy(v -> assertThat(v).containsIgnoringCase("renaming column"));
  }

  @Test
  void shouldRejectAddNotNullColumn() throws Exception {
    // given
    final var changeSets = loadChangeSets("add-not-null-column.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations)
        .as("adding a NOT NULL column must be rejected")
        .hasSize(1)
        .allSatisfy(v -> assertThat(v).contains("add_not_null_column"))
        .allSatisfy(v -> assertThat(v).containsIgnoringCase("nullable"));
  }

  @Test
  void shouldRejectModifyColumnType() throws Exception {
    // given
    final var changeSets = loadChangeSets("modify-column-type.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations)
        .as("modifying a column data type must be rejected")
        .hasSize(1)
        .allSatisfy(v -> assertThat(v).contains("modify_column_type"))
        .allSatisfy(v -> assertThat(v).containsIgnoringCase("changing data type"));
  }

  @Test
  void shouldRejectAddNotNullConstraintOnExistingColumn() throws Exception {
    // given
    final var changeSets = loadChangeSets("add-not-null-constraint.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations)
        .as("adding NOT NULL constraint to existing column must be rejected")
        .hasSize(1)
        .allSatisfy(v -> assertThat(v).contains("add_not_null_constraint"))
        .allSatisfy(v -> assertThat(v).containsIgnoringCase("NOT NULL constraint"));
  }

  @Test
  void shouldRejectAddUniqueConstraintOnExistingWritePath() throws Exception {
    // given
    final var changeSets = loadChangeSets("add-unique-constraint.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations)
        .as("adding UNIQUE constraint on existing write path must be rejected")
        .hasSize(1)
        .allSatisfy(v -> assertThat(v).contains("add_unique_constraint"))
        .allSatisfy(v -> assertThat(v).containsIgnoringCase("UNIQUE constraint"));
  }

  @Test
  void shouldRejectAddForeignKeyConstraintOnExistingWritePath() throws Exception {
    // given
    final var changeSets = loadChangeSets("add-fk-constraint.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations)
        .as("adding FK constraint on existing write path must be rejected")
        .hasSize(1)
        .allSatisfy(v -> assertThat(v).contains("add_fk_constraint"))
        .allSatisfy(v -> assertThat(v).containsIgnoringCase("FOREIGN KEY constraint"));
  }

  @Test
  void shouldAllowCreateTableWithNonNullColumn() throws Exception {
    // given – a new table has no n-1 write paths, so NOT NULL columns are acceptable
    final var changeSets = loadChangeSets("create-table-with-mandatory-column.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then – no violation because new tables have no n-1 writers
    assertThat(violations)
        .as(
            "createTable with a non-nullable non-PK column must be allowed"
                + " (new tables have no n-1 write paths)")
        .isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Production changelog (latest version) must pass validation
  // ---------------------------------------------------------------------------

  @Test
  void shouldPassValidationForCurrentProductionChangelog() throws Exception {
    // given – validate only the latest changeset file (the file for the current release);
    // historical changesets (e.g. 8.9.0) pre-date the rolling-upgrade guardrails and are exempt
    final var changeSets =
        loadChangeSets(null, "db/changelog/rdbms-exporter/changesets/8.10.0.xml");

    // when
    final var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);

    // then
    assertThat(violations)
        .as(
            "The current release changelog must contain only rolling-upgrade-compatible changes."
                + " Violations:\n"
                + String.join("\n", violations))
        .isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  /**
   * Loads changesets from a fixture file under {@code rolling-upgrade-fixtures/} on the classpath.
   */
  private List<ChangeSet> loadChangeSets(final String fixtureFile) throws Exception {
    return loadChangeSets(FIXTURE_BASE, fixtureFile);
  }

  private List<ChangeSet> loadChangeSets(final String base, final String fixtureFile)
      throws Exception {
    final var database = DatabaseFactory.getInstance().getDatabase("h2");
    final var path = base != null ? base + fixtureFile : fixtureFile;
    final var liquibase = new Liquibase(path, new ClassLoaderResourceAccessor(), database);
    liquibase.setChangeLogParameter("prefix", "");
    liquibase.setChangeLogParameter("userCharColumnSize", 256);
    liquibase.setChangeLogParameter("errorMessageSize", 4000);
    liquibase.setChangeLogParameter("treePathSize", 4000);
    return liquibase.getDatabaseChangeLog().getChangeSets();
  }
}
