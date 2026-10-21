/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor.SnapshotFile;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uploads a partition's latest snapshot into the store, if the store doesn't have it yet: its
 * files, the checksum file among them, then its descriptor. The snapshot is reserved while it
 * uploads, so that the snapshot store doesn't delete it. Blocks on the snapshot store's actor, so
 * it must not run on an actor thread.
 */
final class SnapshotUploader {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotUploader.class);
  private static final long RESERVE_TIMEOUT_SECONDS = 30;

  private final LogId log;
  private final ObjectStore store;
  private final PersistedSnapshotStore snapshots;
  private final InstantSource clock;

  SnapshotUploader(
      final LogId log,
      final ObjectStore store,
      final PersistedSnapshotStore snapshots,
      final InstantSource clock) {
    this.log = log;
    this.store = store;
    this.snapshots = snapshots;
    this.clock = clock;
  }

  /**
   * Uploads the latest snapshot, unless there is none, it is a bootstrap snapshot, it is at index
   * 0, or its descriptor is in the store already. A restore from the bucket needs the segments
   * after a snapshot's index, and at index 0 that is the whole log, which it restores without a
   * snapshot.
   *
   * @param history the history the leader publishes in, which the descriptor names
   * @return the descriptor of the snapshot it uploaded, if it uploaded one
   */
  Optional<SnapshotDescriptor> uploadLatest(final UUID history) throws IOException {
    final var latest = snapshots.getLatestSnapshot();
    if (latest.isEmpty() || latest.get().isBootstrap()) {
      return Optional.empty();
    }
    final var snapshot = latest.get();
    if (snapshot.getIndex() == 0) {
      LOG.debug("Not uploading snapshot {} of {}, which is at index 0", snapshot.getId(), log);
      return Optional.empty();
    }
    final var descriptorKey = ObjectNames.snapshotDescriptor(log, snapshot.getId());
    if (store.head(descriptorKey).isPresent()) {
      return Optional.empty();
    }

    final SnapshotReservation reservation;
    try {
      reservation = snapshot.reserve().join(RESERVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (final RuntimeException e) {
      // a newer snapshot replaced it in the meantime; the next call takes that one
      LOG.debug("Not uploading snapshot {} of {}, which is gone", snapshot.getId(), log, e);
      return Optional.empty();
    }
    try {
      final var files = snapshot.files();
      final var descriptor = descriptor(snapshot, files, history);
      SnapshotArchive.upload(store, log, descriptor, files);
      LOG.info(
          "Uploaded snapshot {} of {} at index {}, {} files",
          snapshot.getId(),
          log,
          snapshot.getIndex(),
          descriptor.files().size());
      return Optional.of(descriptor);
    } finally {
      reservation.release();
    }
  }

  private SnapshotDescriptor descriptor(
      final PersistedSnapshot snapshot, final Map<String, Path> files, final UUID history)
      throws IOException {
    final var sizes = new ArrayList<SnapshotFile>();
    for (final var file : new TreeMap<>(files).entrySet()) {
      sizes.add(new SnapshotFile(file.getKey(), Files.size(file.getValue())));
    }
    final var id = snapshot.snapshotId();
    final var metadata = snapshot.getMetadata();
    return new SnapshotDescriptor(
        SnapshotDescriptor.VERSION,
        snapshot.getId(),
        snapshot.getIndex(),
        snapshot.getTerm(),
        id.getProcessedPosition(),
        id.getExportedPosition(),
        metadata.maxExportedPosition(),
        metadata.lastFollowupEventPosition(),
        history,
        sizes,
        clock.instant());
  }
}
