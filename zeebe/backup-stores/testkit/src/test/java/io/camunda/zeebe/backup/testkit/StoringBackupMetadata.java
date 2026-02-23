/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.BackupStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public interface StoringBackupMetadata {
  BackupStore getStore();

  @Test
  default void shouldStoreAndLoad() {
    // given
    final var store = getStore();
    final var content = "{\"partitionId\":1}".getBytes(StandardCharsets.UTF_8);

    // when
    assertThat(store.storeBackupMetadata(1, content)).succeedsWithin(Duration.ofSeconds(10));

    // then
    final var loaded = store.loadBackupMetadata(1).join();
    assertThat(loaded).isPresent();
    assertThat(loaded.get()).isEqualTo(content);
  }

  @Test
  default void shouldReturnEmptyWhenNoMetadataExists() {
    // given
    final var store = getStore();

    // when
    final var loaded = store.loadBackupMetadata(1).join();

    // then
    assertThat(loaded).isEmpty();
  }

  @Test
  default void shouldOverwriteExistingMetadata() {
    // given
    final var store = getStore();
    final var original = "{\"version\":1}".getBytes(StandardCharsets.UTF_8);
    final var updated = "{\"version\":2}".getBytes(StandardCharsets.UTF_8);
    store.storeBackupMetadata(1, original).join();

    // when
    assertThat(store.storeBackupMetadata(1, updated)).succeedsWithin(Duration.ofSeconds(10));

    // then
    final var loaded = store.loadBackupMetadata(1).join();
    assertThat(loaded).isPresent();
    assertThat(loaded.get()).isEqualTo(updated);
  }

  @Test
  default void shouldIsolateMetadataBetweenPartitions() {
    // given
    final var store = getStore();
    final var content1 = "{\"partitionId\":1}".getBytes(StandardCharsets.UTF_8);
    final var content2 = "{\"partitionId\":2}".getBytes(StandardCharsets.UTF_8);

    // when
    store.storeBackupMetadata(1, content1).join();
    store.storeBackupMetadata(2, content2).join();

    // then
    final var loaded1 = store.loadBackupMetadata(1).join();
    final var loaded2 = store.loadBackupMetadata(2).join();
    assertThat(loaded1).isPresent();
    assertThat(loaded1.get()).isEqualTo(content1);
    assertThat(loaded2).isPresent();
    assertThat(loaded2.get()).isEqualTo(content2);
  }
}
