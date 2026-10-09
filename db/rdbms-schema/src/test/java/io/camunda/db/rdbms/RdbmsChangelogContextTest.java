/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;

/**
 * Guards the per-table-prefix Liquibase fast-check workaround in {@code LiquibaseSchemaManager}.
 *
 * <p>Liquibase's fast-check cache key omits the changelog table name, so the runner configures a
 * unique context for every non-blank table prefix. Contextless changesets remain eligible for every
 * tenant, but a context-filtered changeset would be skipped because it cannot match that synthetic
 * context. Adding one must therefore revisit the cache workaround rather than silently changing the
 * migration set.
 */
class RdbmsChangelogContextTest {

  private static final Path MAIN_RESOURCES_DIRECTORY = Path.of("src", "main", "resources");
  private static final Path CHANGELOG_DIRECTORY =
      MAIN_RESOURCES_DIRECTORY.resolve("db/changelog/rdbms-exporter");

  @Test
  void shouldNotFilterRdbmsChangelogsByContext() throws Exception {
    // given every production changelog, including the master, seed, and included changesets
    final List<Path> changelogFiles = changelogFiles();
    assertThat(changelogFiles).isNotEmpty();
    for (final var changelog : changelogFiles) {
      final var changeSets = loadChangeSets(changelog);

      // then the synthetic per-prefix context cannot exclude any changeset
      assertThat(changeSets)
          .isNotEmpty()
          .filteredOn(RdbmsChangelogContextTest::hasContextFilter)
          .as(
              "%s has context-filtered changesets. The per-table-prefix Liquibase fast-check "
                  + "workaround would skip them because its synthetic context cannot match.",
              changelog)
          .isEmpty();
    }
  }

  private static List<Path> changelogFiles() throws IOException {
    try (final var paths = Files.walk(CHANGELOG_DIRECTORY)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".xml"))
          .toList();
    }
  }

  private static boolean hasContextFilter(final ChangeSet changeSet) {
    final var contextFilter = changeSet.getContextFilter();
    return contextFilter != null && !contextFilter.isEmpty();
  }

  private static List<ChangeSet> loadChangeSets(final Path changelog) throws Exception {
    final var database = DatabaseFactory.getInstance().getDatabase("h2");
    final var changelogPath =
        MAIN_RESOURCES_DIRECTORY
            .relativize(changelog)
            .toString()
            .replace(changelog.getFileSystem().getSeparator(), "/");
    final var liquibase =
        new Liquibase(
            changelogPath, new DirectoryResourceAccessor(MAIN_RESOURCES_DIRECTORY), database);
    liquibase.setChangeLogParameter("prefix", "");
    liquibase.setChangeLogParameter("userCharColumnSize", 4000);
    liquibase.setChangeLogParameter("errorMessageSize", 4000);
    liquibase.setChangeLogParameter("treePathSize", 4000);
    return liquibase.getDatabaseChangeLog().getChangeSets();
  }
}
