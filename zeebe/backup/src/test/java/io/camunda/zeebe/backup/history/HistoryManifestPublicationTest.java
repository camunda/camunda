/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class HistoryManifestPublicationTest {
  private final BackupStore store = mock(BackupStore.class);
  private final HistoryManifestCodec codec = new HistoryManifestCodec();

  @Test
  void shouldReadTheEventPlaneContractAndPreserveExactIndexes() throws Exception {
    // given
    try (final var input = getClass().getResourceAsStream("/history/restore.json")) {
      final var manifest = codec.decode(input.readAllBytes());
      // when
      final var restored = codec.decode(codec.encode(manifest));
      // then
      assertThat(restored).isEqualTo(manifest);
      assertThat(restored.partitions().get(1).index()).isEqualTo(900);
      assertThat(restored.partitions().get(2)).isEqualTo(HistoryManifest.Base.root());
      final var large =
          new HistoryManifest(
              1,
              "cluster",
              "default",
              UUID.randomUUID(),
              Instant.now(),
              Map.of(
                  1,
                  new HistoryManifest.Base(UUID.randomUUID(), Long.MAX_VALUE, Long.MAX_VALUE - 1)));
      assertThat(codec.decode(codec.encode(large))).isEqualTo(large);
    }
  }

  @Test
  void shouldRejectIncompleteCheckpointsInsteadOfDefaultingThemToRoot() {
    // given
    final var bytes =
        """
        {"version":1,"clusterId":"cluster","physicalTenantId":"default",
         "historyId":"00000000-0000-4000-8000-000000000001",
         "createdAt":"2026-10-19T12:00:00Z","partitions":{"1":{}}}
        """
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    // when / then
    assertThatThrownBy(() -> codec.decode(bytes)).isInstanceOf(java.io.UncheckedIOException.class);
  }

  @Test
  void shouldPublishOnlyAfterArchivePreparationAndWaitForThePut() {
    // given
    final var manifest = manifest();
    final var prepared = new CompletableFuture<Void>();
    final var stored = new CompletableFuture<Void>();
    when(store.storeHistoryManifest(eq(manifest.historyId()), any())).thenReturn(stored);
    final var publisher = publication(ignored -> prepared);
    // when
    final var result = publisher.publish(manifest);
    // then
    verifyNoInteractions(store);
    assertThat(result).isNotDone();
    prepared.complete(null);
    verify(store).storeHistoryManifest(eq(manifest.historyId()), any());
    assertThat(result).isNotDone();
    stored.complete(null);
    assertThat(result).isCompleted();
  }

  @Test
  void shouldNeverPublishAfterPreparationFails() {
    // given
    final var failed =
        CompletableFuture.<Void>failedFuture(
            new IllegalStateException("Inherited journal is not archived"));
    // when
    final var result = publication(ignored -> failed).publish(manifest());
    // then
    assertThat(result).isCompletedExceptionally();
    verifyNoInteractions(store);
  }

  @Test
  void shouldRetryWithIdenticalManifestBytesAfterAnUncertainPut() {
    // given
    final var manifest = manifest();
    when(store.storeHistoryManifest(any(), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Response lost")))
        .thenReturn(CompletableFuture.completedFuture(null));
    final var publisher = publication(ignored -> CompletableFuture.completedFuture(null));
    // when
    assertThat(publisher.publish(manifest)).isCompletedExceptionally();
    assertThat(publisher.publish(manifest)).isCompleted();
    // then
    final var bytes = ArgumentCaptor.forClass(byte[].class);
    verify(store, times(2)).storeHistoryManifest(eq(manifest.historyId()), bytes.capture());
    assertThat(bytes.getAllValues().getFirst()).isEqualTo(bytes.getAllValues().getLast());
  }

  @Test
  void shouldRejectObjectsFromAnotherHistoryOrEnvironment() {
    // given
    final var manifest = manifest();
    final var requested = UUID.randomUUID();
    when(store.loadHistoryManifest(requested))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(codec.encode(manifest))));
    final var publisher = publication(ignored -> CompletableFuture.completedFuture(null));
    // when / then
    assertThat(publisher.load(requested)).isCompletedExceptionally();
    assertThatThrownBy(
            () ->
                publisher.publish(
                    new HistoryManifest(
                        1,
                        "other",
                        "default",
                        UUID.randomUUID(),
                        Instant.now(),
                        Map.of(1, HistoryManifest.Base.root()))))
        .isInstanceOf(IllegalArgumentException.class);
    verify(store, never()).storeHistoryManifest(any(), any());
  }

  @Test
  void shouldPreserveBackupProvenanceAndReadLegacyDescriptors() throws Exception {
    // given
    final var mapper =
        JsonMapper.builder().addModule(new Jdk8Module()).addModule(new JavaTimeModule()).build();
    final var legacy =
        new BackupDescriptorImpl(100, 1, "test", Instant.now(), CheckpointType.MANUAL_BACKUP);
    final var history = UUID.randomUUID();
    final var descriptor = legacy.withHistoryId(history);
    // when
    final var restored =
        mapper.readValue(mapper.writeValueAsBytes(descriptor), BackupDescriptorImpl.class);
    final var tree = mapper.valueToTree(legacy);
    ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove("historyId");
    // then
    assertThat(restored.historyId()).contains(history);
    assertThat(BackupDescriptorImpl.from(restored).historyId()).contains(history);
    assertThat(mapper.treeToValue(tree, BackupDescriptorImpl.class).historyId()).isEmpty();
  }

  private HistoryManifestPublication publication(
      final HistoryManifestPublication.Preparation preparation) {
    return new HistoryManifestPublication(store, "cluster", "default", preparation);
  }

  private static HistoryManifest manifest() {
    return new HistoryManifest(
        1,
        "cluster",
        "default",
        UUID.randomUUID(),
        Instant.now(),
        Map.of(1, HistoryManifest.Base.root()));
  }
}
