/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RestoreManagerTest {

  @Test
  void shouldFailWhenDirectoryIsNotEmpty(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry());

    // when
    Files.createDirectory(dir.resolve("other-data"));

    // then
    assertThatThrownBy(() -> restoreManager.restore(1L, false, List.of()))
        .isInstanceOf(DirectoryNotEmptyException.class);
  }

  @Test
  void shouldNotFailOnLostAndFoundDirectory(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry());

    // when
    Files.createDirectory(dir.resolve("lost+found"));

    // then
    assertThatThrownBy(() -> restoreManager.restore(1L, false, List.of()))
        .hasRootCauseInstanceOf(BackupNotFoundException.class);
  }

  @Test
  void shouldIgnoreConfigurableFilesInTarget(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry());

    // when - create ignored files
    Files.createDirectory(dir.resolve("lost+found"));
    Files.createFile(dir.resolve(".DS_Store"));
    Files.createFile(dir.resolve("Thumbs.db"));

    // then - should not fail because all files are ignored
    assertThatThrownBy(
            () ->
                restoreManager.restore(1L, false, List.of("lost+found", ".DS_Store", "Thumbs.db")))
        .hasRootCauseInstanceOf(BackupNotFoundException.class);
  }

  @Test
  void shouldFailWhenNonIgnoredFileExists(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry());

    // when - create ignored and non-ignored files
    Files.createDirectory(dir.resolve("lost+found"));
    Files.createFile(dir.resolve("some-data-file"));

    // then - should fail because some-data-file is not ignored
    assertThatThrownBy(() -> restoreManager.restore(1L, false, List.of("lost+found")))
        .isInstanceOf(DirectoryNotEmptyException.class);
  }
}
