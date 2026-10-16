/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.backup.testkit.LogArchiveStoreContractTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FilesystemLogArchiveStoreTest extends LogArchiveStoreContractTest {

  @TempDir Path baseDir;
  private FilesystemLogArchiveStore store;

  @BeforeEach
  void setUp() {
    store =
        FilesystemLogArchiveStore.of(
            new FilesystemBackupConfig.Builder().withBasePath(baseDir.toString()).build());
  }

  @AfterEach
  void tearDown() {
    store.closeAsync().join();
  }

  @Override
  protected LogArchiveStore store() {
    return store;
  }

  @Test
  void shouldNotLeaveTemporaryFilesBehind() throws IOException {
    // given
    store.put("log-archive/a", new byte[] {1}).join();

    // when
    final var listed = store.list("").join();

    // then
    assertThat(listed).containsExactly("log-archive/a");
    try (final var tmp = Files.list(baseDir.resolve(".tmp"))) {
      assertThat(tmp).isEmpty();
    }
  }

  @Test
  void shouldRejectKeysEscapingBasePath() {
    assertThatThrownBy(() -> store.put("../escape", new byte[] {1}).join())
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }
}
