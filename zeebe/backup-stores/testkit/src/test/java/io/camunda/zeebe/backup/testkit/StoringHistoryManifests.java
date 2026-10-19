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
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public interface StoringHistoryManifests {
  BackupStore getStore();

  @Test
  default void shouldReturnEmptyForAnUnknownHistory() {
    // given
    final var id = UUID.randomUUID();
    // when / then
    assertThat(getStore().loadHistoryManifest(id).join()).isEmpty();
  }

  @Test
  default void shouldPreserveIndependentHistoriesAndIdenticalRetries() {
    // given
    final var first = UUID.randomUUID();
    final var second = UUID.randomUUID();
    final var original = "{\"version\":1}".getBytes(StandardCharsets.UTF_8);
    final var other = "{\"version\":2}".getBytes(StandardCharsets.UTF_8);
    // when
    getStore().storeHistoryManifest(first, original).join();
    getStore().storeHistoryManifest(second, other).join();
    getStore().storeHistoryManifest(first, original).join();
    // then
    assertThat(getStore().loadHistoryManifest(first).join().orElseThrow()).isEqualTo(original);
    assertThat(getStore().loadHistoryManifest(second).join().orElseThrow()).isEqualTo(other);
  }

  @Test
  default void shouldPreserveProvenanceAndKeepHistoryAfterBackupDeletion() throws IOException {
    // given
    final var history = UUID.randomUUID();
    final var content = "{\"version\":1}".getBytes(StandardCharsets.UTF_8);
    final var original = TestBackupProvider.simpleBackup();
    final var backup =
        new BackupImpl(
            original.id(),
            BackupDescriptorImpl.from(original.descriptor()).withHistoryId(history),
            original.snapshot(),
            original.segments());
    getStore().storeHistoryManifest(history, content).join();
    // when
    getStore().save(backup).join();
    // then
    assertThat(getStore().getStatus(backup.id()).join().descriptor().orElseThrow().historyId())
        .contains(history);
    // when
    getStore().markDeleted(backup.id()).join();
    getStore().delete(backup.id()).join();
    // then
    assertThat(getStore().loadHistoryManifest(history).join().orElseThrow()).isEqualTo(content);
  }
}
