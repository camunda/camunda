/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.history;

import io.camunda.zeebe.backup.api.BackupStore;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Publishes activation metadata only after archive preparation and installation have succeeded.
 * There is deliberately no production preparation implementation until segment publishing exists.
 */
@NullMarked
public final class HistoryManifestPublication {
  private final BackupStore store;
  private final String clusterId;
  private final String physicalTenantId;
  private final Preparation preparation;
  private final HistoryManifestCodec codec = new HistoryManifestCodec();

  public HistoryManifestPublication(
      final BackupStore store,
      final String clusterId,
      final String physicalTenantId,
      final Preparation preparation) {
    this.store = Objects.requireNonNull(store);
    this.clusterId = Objects.requireNonNull(clusterId);
    this.physicalTenantId = Objects.requireNonNull(physicalTenantId);
    this.preparation = Objects.requireNonNull(preparation);
  }

  /**
   * Retries must reuse the persisted manifest, including its UUID and timestamp. The returned
   * future completes only after the ordinary object PUT; processing must remain stopped until then.
   */
  public CompletableFuture<Void> publish(final HistoryManifest manifest) {
    requireEnvironment(manifest);
    final var bytes = codec.encode(manifest);
    return preparation
        .prepare(manifest)
        .thenCompose(ignored -> store.storeHistoryManifest(manifest.historyId(), bytes));
  }

  public CompletableFuture<Optional<HistoryManifest>> load(final UUID historyId) {
    return store
        .loadHistoryManifest(historyId)
        .thenApply(
            bytes ->
                bytes.map(
                    content -> {
                      final var manifest = codec.decode(content);
                      if (!manifest.historyId().equals(historyId)) {
                        throw new IllegalArgumentException(
                            "Stored manifest does not match the requested history");
                      }
                      requireEnvironment(manifest);
                      return manifest;
                    }));
  }

  private void requireEnvironment(final HistoryManifest manifest) {
    if (!clusterId.equals(manifest.clusterId())
        || !physicalTenantId.equals(manifest.physicalTenantId())) {
      throw new IllegalArgumentException("Manifest belongs to another environment");
    }
  }

  @FunctionalInterface
  public interface Preparation {
    /**
     * Complete only after inherited journal publication and durable installation of the common
     * history/checkpoints on all participating brokers. Backup metadata alone is insufficient.
     */
    CompletableFuture<Void> prepare(HistoryManifest manifest);
  }
}
