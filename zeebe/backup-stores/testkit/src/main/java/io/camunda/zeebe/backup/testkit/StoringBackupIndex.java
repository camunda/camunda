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
import java.time.Duration;
import org.junit.jupiter.api.Test;

public interface StoringBackupIndex {
  BackupStore getStore();

  @Test
  default void restoresEmptyIndex() {
    // given
    final var store = getStore();

    // when
    final var index = store.restoreIndex(new BackupIndexIdentifierImpl(1, 1)).join();

    // then
    assertThat(index.path()).isEmptyFile();
  }

  @Test
  default void canUploadEmptyIndex() {
    // given
    final var store = getStore();

    // when
    final var index = store.restoreIndex(new BackupIndexIdentifierImpl(1, 1)).join();

    // then
    assertThat(store.storeIndex(index)).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  default void canUploadModifiedIndex() throws IOException {
    // given
    final var store = getStore();
    final var index = store.restoreIndex(new BackupIndexIdentifierImpl(1, 1)).join();

    // when
    Files.write(index.path(), "some index content".getBytes());

    // then
    assertThat(store.storeIndex(index)).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  default void canRestoreModifiedIndex() throws IOException {
    // given
    final var store = getStore();
    final var id = new BackupIndexIdentifierImpl(1, 1);
    final var originalIndex = store.restoreIndex(id).join();
    Files.write(originalIndex.path(), "some index content".getBytes());

    // when
    store.storeIndex(originalIndex).join();

    // then
    final var restoredIndex =
        assertThat(store.restoreIndex(id)).succeedsWithin(Duration.ofSeconds(10)).actual();
    assertThat(restoredIndex.path()).isNotEqualTo(originalIndex.path());
    assertThat(restoredIndex.path()).hasSameBinaryContentAs(originalIndex.path());
  }

  @Test
  default void canUploadMultipleTimes() throws IOException {
    // given
    final var store = getStore();
    final var id = new BackupIndexIdentifierImpl(1, 1);
    final var index = store.restoreIndex(id).join();

    // when
    Files.write(index.path(), "some index content".getBytes());
    store.storeIndex(index).join();
    Files.write(index.path(), "different index content".getBytes());
    store.storeIndex(index).join();

    // then
    final var restoredIndex =
        assertThat(store.restoreIndex(id)).succeedsWithin(Duration.ofSeconds(10)).actual();
    assertThat(restoredIndex.path()).isNotEqualTo(index.path());
    assertThat(restoredIndex.path()).hasSameBinaryContentAs(index.path());
  }
}
