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
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies that frozen Liquibase changeset files are never modified after release.
 *
 * <p>Liquibase stores a checksum of every executed changeset in {@code DATABASECHANGELOG}. On every
 * subsequent startup it recomputes and compares the checksum; a mismatch causes the application to
 * refuse to start on existing installations. Any modification to a released changeset — even
 * whitespace — is therefore a breaking change for existing users.
 *
 * <p>This test byte-compares each file in the {@code golden/} directory against its counterpart in
 * the main resources. Adding a golden file for a version is the only step needed to freeze it — no
 * code changes are required. To freeze a new version at release time:
 *
 * <pre>{@code
 * cp db/rdbms-schema/src/main/resources/db/changelog/rdbms-exporter/changesets/8.10.0.xml \
 *    db/rdbms-schema/src/test/resources/db/changelog/rdbms-exporter/changesets/golden/8.10.0.xml
 * }</pre>
 *
 * <p>If this test fails, do not update the golden file. Instead, revert the change to the frozen
 * changeset and introduce your schema change as a new versioned changeset file.
 */
class ChangelogGoldenFilesTest {

  // Surefire runs from the module directory (db/rdbms-schema/), so these are module-relative.
  private static final String CHANGESETS_SOURCE =
      "src/main/resources/db/changelog/rdbms-exporter/changesets";
  private static final String GOLDEN_DIR =
      "src/test/resources/db/changelog/rdbms-exporter/changesets/golden";

  // Repo-root-relative paths, used only in error messages.
  private static final String MODULE_PATH = "db/rdbms-schema/";

  static Stream<Path> frozenChangesets() throws IOException {
    return Files.list(Paths.get(GOLDEN_DIR)).filter(p -> p.toString().endsWith(".xml")).sorted();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("frozenChangesets")
  void shouldNotChangeFrozenChangeset(final Path goldenFilePath) throws IOException {
    final var filename = goldenFilePath.getFileName().toString();
    final var sourceFilePath = Paths.get(CHANGESETS_SOURCE).resolve(filename);

    final var repoSourcePath = MODULE_PATH + CHANGESETS_SOURCE + "/" + filename;
    final var repoGoldenPath = MODULE_PATH + GOLDEN_DIR + "/" + filename;

    // given
    assertThat(sourceFilePath.toFile())
        .describedAs(
            """
            Frozen changeset '%s' no longer exists in the source tree.
            Deleting a released changeset breaks existing installations — restore it.""",
            filename)
        .exists();

    // when / then
    final var goldenContents = Files.readString(goldenFilePath);
    final var sourceContents = Files.readString(sourceFilePath);

    assertThat(sourceContents)
        .describedAs(
            """
            Frozen changeset '%s' must not be modified after release.
            Liquibase stores a checksum of every executed changeset; changing the file \
            causes a checksum mismatch on existing installations.
            To add new schema changes, create a new versioned changeset file instead.
            If you are certain this change is safe, update the golden file with:
              cp %s %s""",
            filename, repoSourcePath, repoGoldenPath)
        .isEqualTo(goldenContents);
  }
}
