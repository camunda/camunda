/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.zeebe.eventplane.history.HistoryIds;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.history.ManifestJson;
import io.camunda.zeebe.eventplane.history.MissingManifestException;
import io.camunda.zeebe.eventplane.history.RestoredHistory;
import io.camunda.zeebe.eventplane.publisher.Announcer;
import io.camunda.zeebe.eventplane.publisher.CutPolicy;
import io.camunda.zeebe.eventplane.publisher.EntrySource;
import io.camunda.zeebe.eventplane.publisher.FloorStore;
import io.camunda.zeebe.eventplane.publisher.PartitionPublisher;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import io.camunda.zeebe.eventplane.segment.Identity;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes one partition while its broker leads it, on a thread of its own: the uploads block, and
 * so does the floor, which waits on the exporter director. A failed poll is tried again on the next
 * one; the publisher keeps the segment it couldn't upload.
 */
final class PublisherService implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PublisherService.class);

  private final Identity log;
  private final String clusterKey;
  private final Path partitionDirectory;
  private final ObjectStore store;
  private final EntrySource source;
  private final FloorStore floors;
  private final Announcer announcer;
  private final CutPolicy policy;
  private final ScheduledExecutorService executor;
  private @Nullable PartitionPublisher publisher;

  PublisherService(
      final Identity log,
      final String clusterKey,
      final Path partitionDirectory,
      final ObjectStore store,
      final EntrySource source,
      final FloorStore floors,
      final Announcer announcer,
      final CutPolicy policy) {
    this.log = log;
    this.clusterKey = clusterKey;
    this.partitionDirectory = partitionDirectory;
    this.store = store;
    this.source = source;
    this.floors = floors;
    this.announcer = announcer;
    this.policy = policy;
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable ->
                Thread.ofPlatform()
                    .name("event-plane-publisher-%s-%d".formatted(log.tenantId(), log.partition()))
                    .daemon()
                    .unstarted(runnable));
  }

  void start(final Duration pollInterval) {
    executor.scheduleWithFixedDelay(this::poll, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Stops polling, and closes the publisher, the source and the store on the publisher's thread.
   */
  CompletableFuture<Void> closeAsync() {
    final var closed = new CompletableFuture<Void>();
    executor.execute(
        () -> {
          try {
            close();
            closed.complete(null);
          } catch (final Exception e) {
            closed.completeExceptionally(e);
          }
        });
    executor.shutdown();
    return closed;
  }

  @Override
  public void close() throws Exception {
    try (store;
        source;
        announcer) {
      if (publisher != null) {
        publisher.close();
        publisher = null;
      }
    }
  }

  private void poll() {
    try {
      if (publisher == null) {
        publisher = open();
      }
      publisher.poll();
    } catch (final Exception e) {
      LOG.warn("Failed to publish {}, trying again", log, e);
    }
  }

  private PartitionPublisher open() throws IOException {
    final var manifest = manifest(floors.load());
    final var opened =
        new PartitionPublisher(
            log, manifest, source, store, floors, announcer, policy, InstantSource.system());
    opened.start();
    LOG.info("Publishing {} in history {}", log, manifest.historyId());
    return opened;
  }

  /**
   * The history to publish in: the one a restore left, else the floor's, else the tenant's first.
   * See "The history file after a restore" in zeebe/event-plane/docs/contracts.md.
   */
  private HistoryManifest manifest(final @Nullable UploadFloor floor) throws IOException {
    final var initial = HistoryIds.initial(clusterKey, log.tenantId());
    final var restored = RestoredHistory.readFrom(partitionDirectory);
    if (restored.isPresent()
        && (floor == null || !floor.history().equals(restored.get().history()))) {
      // the first start after the restore: the restored floor names the backup's history
      final var history = restored.get();
      final Base base;
      if (history.source() != null) {
        base = history.baseIn(history.source());
      } else if (floor != null) {
        base = history.baseIn(floor.history());
      } else {
        base = storedPart(initial).isPresent() ? history.baseIn(initial) : Base.NOTHING;
      }
      return part(history.history(), history.createdAt(), base);
    }
    if (floor == null || floor.history().equals(initial)) {
      // the same bytes from every leader, and older than every restore
      return part(initial, Instant.EPOCH, Base.NOTHING);
    }
    // the floor's history wrote its part before its first upload
    return storedPart(floor.history())
        .orElseThrow(() -> new MissingManifestException(floor.history()));
  }

  private Optional<HistoryManifest> storedPart(final UUID history) throws IOException {
    final var key =
        ObjectNames.historyPart(log.clusterId(), log.tenantId(), history, log.partition());
    final var content = store.get(key);
    return content.isPresent() ? Optional.of(ManifestJson.read(content.get())) : Optional.empty();
  }

  private HistoryManifest part(final UUID history, final Instant createdAt, final Base base) {
    return new HistoryManifest(
        1, log.clusterId(), log.tenantId(), history, createdAt, Map.of(log.partition(), base));
  }
}
