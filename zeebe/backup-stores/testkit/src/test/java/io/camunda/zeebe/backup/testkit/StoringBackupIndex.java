/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIndexIdentifierImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public interface StoringBackupIndex {
  BackupStore getStore();

  @Test
  default void doesNotOverwriteExistingFileWhenRestoringIndex(@TempDir final Path targetPath)
      throws IOException {
    // given
    final var store = getStore();
    final var existingFile = Files.createFile(targetPath.resolve("index"));

    // when / then
    assertThatThrownBy(
            () -> store.restoreIndex(new BackupIndexIdentifierImpl(1, 1), existingFile).join())
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Index file already exists at");
  }

  @Test
  default void restoresEmptyIndex(@TempDir final Path targetPath) {
    // given
    final var store = getStore();

    // when
    final var index =
        store.restoreIndex(new BackupIndexIdentifierImpl(1, 1), targetPath.resolve("index")).join();

    // then
    assertThat(index.path()).isEmptyFile();
  }

  @Test
  default void canUploadEmptyIndex(@TempDir final Path targetPath) {
    // given
    final var store = getStore();

    // when
    final var index =
        store.restoreIndex(new BackupIndexIdentifierImpl(1, 1), targetPath.resolve("index")).join();

    // then
    assertThat(store.storeIndex(index)).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  default void canUploadModifiedIndex(@TempDir final Path targetPath) throws IOException {
    // given
    final var store = getStore();
    final var index =
        store.restoreIndex(new BackupIndexIdentifierImpl(1, 1), targetPath.resolve("index")).join();

    // when
    Files.write(index.path(), "some index content".getBytes());

    // then
    assertThat(store.storeIndex(index)).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  default void canRestoreModifiedIndex(@TempDir final Path targetPath) throws IOException {
    // given
    final var store = getStore();
    final var id = new BackupIndexIdentifierImpl(1, 1);
    final var originalIndex = store.restoreIndex(id, targetPath.resolve("index1")).join();
    Files.write(originalIndex.path(), "some index content".getBytes());

    // when
    store.storeIndex(originalIndex).join();

    // then
    final var restoredIndex =
        assertThat(store.restoreIndex(id, targetPath.resolve("index2")))
            .succeedsWithin(Duration.ofSeconds(10))
            .actual();
    assertThat(restoredIndex.path()).isNotEqualTo(originalIndex.path());
    assertThat(restoredIndex.path()).hasSameBinaryContentAs(originalIndex.path());
  }

  @Test
  default void canUploadMultipleTimes(@TempDir final Path targetPath) throws IOException {
    // given
    final var store = getStore();
    final var id = new BackupIndexIdentifierImpl(1, 1);
    final var index = store.restoreIndex(id, targetPath.resolve("index1")).join();

    // when
    Files.write(index.path(), "some index content".getBytes());
    store.storeIndex(index).join();
    Files.write(index.path(), "different index content".getBytes());
    store.storeIndex(index).join();

    // then
    final var restoredIndex =
        assertThat(store.restoreIndex(id, targetPath.resolve("index2")))
            .succeedsWithin(Duration.ofSeconds(10))
            .actual();
    assertThat(restoredIndex.path()).isNotEqualTo(index.path());
    assertThat(restoredIndex.path()).hasSameBinaryContentAs(index.path());
  }
}
