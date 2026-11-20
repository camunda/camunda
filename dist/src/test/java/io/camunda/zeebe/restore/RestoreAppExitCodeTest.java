/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ExitCodeEvent;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;

/**
 * Runs the restore app as a Spring Boot application, as its main method does, and checks two parts
 * of the exit code contract: a failed restore exits with the code of its reason, and leaves no data
 * behind, so the caller can run the restore again on the same volume.
 *
 * <p>The exit itself happens on the main thread only, so the test observes the {@link
 * ExitCodeEvent} Spring Boot publishes for it.
 */
final class RestoreAppExitCodeTest {

  @TempDir private Path backupDirectory;
  @TempDir private Path dataDirectory;

  private final List<Integer> exitCodes = new ArrayList<>();

  @Test
  void shouldExitWithTheInvalidRequestCodeForANegativeBackupId() throws IOException {
    // when
    assertThatThrownBy(() -> runRestore("--backupId=-1"))
        .hasRootCauseMessage("Backup id must not be negative but was -1");

    // then
    assertThat(exitCodes).containsExactly(10);
    assertDataDirectoryIsEmpty();
  }

  @Test
  void shouldExitWithTheBackupsInconsistentCodeWhenTheBackupDoesNotExist() throws IOException {
    // when
    assertThatThrownBy(() -> runRestore("--backupId=42"))
        .hasRootCauseMessage("Could not find a completed backup with id 42.");

    // then
    assertThat(exitCodes).containsExactly(13);
    assertDataDirectoryIsEmpty();
  }

  private void runRestore(final String... args) {
    new SpringApplicationBuilder(RestoreApp.class)
        .web(WebApplicationType.NONE)
        .profiles(Profile.RESTORE.getId())
        .listeners((ApplicationListener<ExitCodeEvent>) e -> exitCodes.add(e.getExitCode()))
        .properties(
            "camunda.data.primary-storage.directory=" + dataDirectory,
            "camunda.data.backup.store=filesystem",
            "camunda.data.backup.filesystem.basepath=" + backupDirectory,
            "camunda.cluster.node-id=0")
        .run(args);
  }

  /** A failed restore clears the data directory, so it can run again on the same volume. */
  private void assertDataDirectoryIsEmpty() throws IOException {
    try (final Stream<Path> files = Files.walk(dataDirectory)) {
      assertThat(files.filter(Files::isRegularFile)).isEmpty();
    }
  }
}
