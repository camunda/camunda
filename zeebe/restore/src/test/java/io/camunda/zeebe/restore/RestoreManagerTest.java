/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    assertThat(restoreManager.restore(1L, false))
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(DirectoryNotEmptyException.class)
        .isNotNull();
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
    assertThat(restoreManager.restore(1L, false))
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(BackupNotFoundException.class)
        .isNotNull();
  }
}
