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

public interface StoringHandoverRecords {
  BackupStore getStore();

  @Test
  default void shouldStoreAndLoadHandoverRecord() {
    // given
    final var store = getStore();
    final var content = "handover".getBytes(StandardCharsets.UTF_8);

    // when
    assertThat(store.storeHandoverRecord(1, 42, content)).succeedsWithin(Duration.ofSeconds(10));

    // then
    final var loaded = store.loadHandoverRecords(1).join();
    assertThat(loaded).containsOnlyKeys(42L);
    assertThat(loaded.get(42L)).isEqualTo(content);
  }

  @Test
  default void shouldReturnNoHandoverRecordsWhenNoneExists() {
    // given
    final var store = getStore();

    // when
    final var loaded = store.loadHandoverRecords(1).join();

    // then
    assertThat(loaded).isEmpty();
  }

  @Test
  default void shouldStoreSameHandoverRecordTwice() {
    // given
    final var store = getStore();
    final var content = "handover".getBytes(StandardCharsets.UTF_8);
    store.storeHandoverRecord(1, 42, content).join();

    // when
    assertThat(store.storeHandoverRecord(1, 42, content)).succeedsWithin(Duration.ofSeconds(10));

    // then
    final var loaded = store.loadHandoverRecords(1).join();
    assertThat(loaded).containsOnlyKeys(42L);
    assertThat(loaded.get(42L)).isEqualTo(content);
  }

  @Test
  default void shouldKeepHandoverRecordsOfDifferentCheckpoints() {
    // given
    final var store = getStore();
    final var first = "first".getBytes(StandardCharsets.UTF_8);
    final var second = "second".getBytes(StandardCharsets.UTF_8);

    // when - stored out of order
    store.storeHandoverRecord(1, 200, second).join();
    store.storeHandoverRecord(1, 100, first).join();

    // then - listed by ascending checkpoint id
    final var loaded = store.loadHandoverRecords(1).join();
    assertThat(loaded.keySet()).containsExactly(100L, 200L);
    assertThat(loaded.get(100L)).isEqualTo(first);
    assertThat(loaded.get(200L)).isEqualTo(second);
  }

  @Test
  default void shouldIsolateHandoverRecordsBetweenPartitions() {
    // given
    final var store = getStore();
    final var content1 = "content1".getBytes(StandardCharsets.UTF_8);
    final var content2 = "content2".getBytes(StandardCharsets.UTF_8);

    // when
    store.storeHandoverRecord(1, 42, content1).join();
    store.storeHandoverRecord(2, 43, content2).join();

    // then
    final var loaded1 = store.loadHandoverRecords(1).join();
    final var loaded2 = store.loadHandoverRecords(2).join();
    assertThat(loaded1).containsOnlyKeys(42L);
    assertThat(loaded1.get(42L)).isEqualTo(content1);
    assertThat(loaded2).containsOnlyKeys(43L);
    assertThat(loaded2.get(43L)).isEqualTo(content2);
  }

  @Test
  default void shouldNotTouchBackupMetadataWhenStoringHandoverRecord() {
    // given
    final var store = getStore();
    final var metadata = "metadata".getBytes(StandardCharsets.UTF_8);
    store.storeBackupMetadata(1, metadata).join();

    // when
    store.storeHandoverRecord(1, 42, "handover".getBytes(StandardCharsets.UTF_8)).join();

    // then
    assertThat(store.loadBackupMetadata(1).join()).hasValue(metadata);
  }
}
