/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.ListOptions;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.eventplane.backup.BackupLog;
import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.history.MissingManifestException;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.NoSuchObjectException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.restore.BackupLogException.NoBackupLog;
import io.camunda.zeebe.restore.BackupLogException.NotInBucket;
import io.camunda.zeebe.restore.BackupLogException.NotUploaded;
import io.camunda.zeebe.restore.BucketRestoreException.NoHistory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A backup store whose backups keep their log in the Event Plane's bucket instead of copying the
 * journal's segments. It wraps the configured backup store, which keeps the rest of a backup: its
 * snapshot, status and descriptor. See "Backups whose log is in the bucket" in
 * zeebe/event-plane/docs/contracts.md.
 */
public final class EventPlaneLogBackupStore implements BackupStore {

  private static final Logger LOG = LoggerFactory.getLogger(EventPlaneLogBackupStore.class);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
  private static final String JOURNAL_SUFFIX = ".log";

  private final BackupStore delegate;
  private final ObjectStore store;
  private final String cluster;
  private final String tenant;
  private final Duration uploadTimeout;
  private final PublishingHistory publishing;
  private final InstantSource clock;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  EventPlaneLogBackupStore(
      final BackupStore delegate,
      final ObjectStore store,
      final String cluster,
      final String tenant,
      final Duration uploadTimeout,
      final PublishingHistory publishing,
      final InstantSource clock) {
    this.delegate = delegate;
    this.store = store;
    this.cluster = cluster;
    this.tenant = tenant;
    this.uploadTimeout = uploadTimeout;
    this.publishing = publishing;
    this.clock = clock;
  }

  /**
   * Wraps a backup store, so that its backups keep their log in the Event Plane's bucket.
   *
   * @param store the Event Plane's store, which this store owns and closes
   * @param cluster the cluster id in the store's object names, the publisher's
   * @param tenant the physical tenant whose partitions it backs up
   * @param uploadTimeout how long a backup waits for the bucket to hold its checkpoint
   * @param publishing the history each partition's leader publishes in
   */
  public static BackupStore wrap(
      final BackupStore delegate,
      final ObjectStore store,
      final String cluster,
      final String tenant,
      final Duration uploadTimeout,
      final PublishingHistory publishing) {
    return new EventPlaneLogBackupStore(
        delegate, store, cluster, tenant, uploadTimeout, publishing, InstantSource.system());
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    return CompletableFuture.runAsync(() -> unchecked(() -> writeBackupLog(backup)), executor)
        .thenCompose(
            ignored ->
                delegate.save(
                    new BackupImpl(
                        backup.id(),
                        backup.descriptor(),
                        backup.snapshot(),
                        new NamedFileSetImpl(Map.of()))));
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    return delegate.getStatus(id);
  }

  @Override
  public CompletableFuture<List<BackupStatus>> list(
      final BackupIdentifierWildcard wildcard, final ListOptions options) {
    return delegate.list(wildcard, options);
  }

  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    return delegate.list(wildcard);
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    return delegate.delete(id).thenCompose(ignored -> deleteBackupLog(id));
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    return delegate
        .restore(id, targetFolder)
        .thenApplyAsync(
            backup ->
                backup.segments().names().isEmpty()
                    ? unchecked(() -> withLogFromBucket(backup, targetFolder))
                    : backup,
            executor);
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    return delegate
        .markFailed(id, failureReason)
        .thenCompose(code -> deleteBackupLog(id).thenApply(ignored -> code));
  }

  @Override
  public CompletableFuture<BackupStatusCode> markDeleted(final BackupIdentifier id) {
    return delegate.markDeleted(id);
  }

  @Override
  public CompletableFuture<Void> storeBackupMetadata(final int partitionId, final byte[] content) {
    return delegate.storeBackupMetadata(partitionId, content);
  }

  @Override
  public CompletableFuture<Optional<byte[]>> loadBackupMetadata(final int partitionId) {
    return delegate.loadBackupMetadata(partitionId);
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    return delegate
        .closeAsync()
        .whenComplete(
            (ignored, error) -> {
              executor.shutdownNow();
              try {
                store.close();
              } catch (final Exception e) {
                LOG.warn("Failed to close the Event Plane's store {}", store.root(), e);
              }
            });
  }

  @Override
  public CompletableFuture<Void> verifyConnection() {
    return delegate.verifyConnection();
  }

  /**
   * Waits until the bucket holds the backup's checkpoint in the history the partition's leader
   * publishes in, and writes what the backup needs of the log.
   */
  private void writeBackupLog(final Backup backup) throws IOException, InterruptedException {
    final var id = backup.id();
    final var log = log(id.partitionId());
    final var checkpointPosition = backup.descriptor().checkpointPosition();
    final var deadline = clock.instant().plus(uploadTimeout);
    while (true) {
      final var found = find(log, backup);
      if (found instanceof final Found.Located located) {
        final var backupLog =
            new BackupLog(
                BackupLog.VERSION,
                id.checkpointId(),
                checkpointPosition,
                located.history(),
                located.firstIndex(),
                located.lastIndex(),
                clock.instant());
        store.put(ObjectNames.backupLog(log, id.checkpointId()), backupLog.toJson());
        LOG.info(
            "Backup {} keeps its log in the Event Plane's bucket: {}, entries {} to {} of history {}",
            id,
            log,
            located.firstIndex(),
            located.lastIndex(),
            located.history());
        return;
      }
      if (!clock.instant().isBefore(deadline)) {
        throw new NotUploaded(
            id.partitionId(),
            id.checkpointId(),
            checkpointPosition,
            uploadTimeout,
            ((Found.Behind) found).uploadedPosition());
      }
      Thread.sleep(POLL_INTERVAL);
    }
  }

  /** Where the backup's log lies in the bucket, or how far the bucket reaches if not far enough. */
  private Found find(final LogId log, final Backup backup) throws IOException {
    final var history = publishing.of(log.partition());
    if (history.isEmpty()) {
      return new Found.Behind(-1);
    }
    final List<Stretch> path;
    try {
      path = Histories.load(store, cluster, tenant).path(history.get(), log.partition());
    } catch (final MissingManifestException e) {
      // the leader writes its history's part before it uploads in it
      return new Found.Behind(-1);
    }
    if (path.isEmpty()) {
      return new Found.Behind(-1);
    }
    final var segments = new PathSegments(store, log, path);
    segments.refresh();
    try {
      return locate(history.get(), new PathEntries(store, log, segments), segments, backup);
    } catch (final NoSuchObjectException e) {
      // maintenance replaced a segment while it was read: look again in the next round
      LOG.debug("A segment of {} disappeared while locating backup {}", log, backup.id(), e);
      return new Found.Behind(-1);
    }
  }

  /**
   * Walks back from the path's newest segment, by the segments' footers: to the one that holds the
   * checkpoint position, and on to the one that holds the backup's first log position.
   */
  private Found locate(
      final UUID history,
      final PathEntries entries,
      final PathSegments segments,
      final Backup backup)
      throws IOException {
    final var id = backup.id();
    final var checkpointPosition = backup.descriptor().checkpointPosition();
    final OptionalLong firstLogPosition = backup.descriptor().firstLogPosition();
    final var newest = segments.lastIndex();
    if (newest.isEmpty()) {
      return new Found.Behind(-1);
    }
    long index = newest.getAsLong();
    long lastIndex = -1;
    boolean sawRecords = false;
    while (index >= 1) {
      final var segment = segments.holding(index);
      if (segment.isEmpty()) {
        throw new NotInBucket(
            id.partitionId(),
            id.checkpointId(),
            lastIndex == -1 ? checkpointPosition : firstLogPosition.orElse(checkpointPosition),
            index);
      }
      final var footer = entries.footer(segment.get());
      if (footer.hasRecords()) {
        if (!sawRecords && footer.lastPosition() < checkpointPosition) {
          return new Found.Behind(footer.lastPosition());
        }
        sawRecords = true;
        if (lastIndex == -1 && footer.firstPosition() <= checkpointPosition) {
          lastIndex = segment.get().lastServed();
          if (firstLogPosition.isEmpty()) {
            return new Found.Located(history, segments.firstIndex().orElseThrow(), lastIndex);
          }
        }
        if (lastIndex != -1 && footer.firstPosition() <= firstLogPosition.orElseThrow()) {
          return new Found.Located(history, segment.get().name().firstIndex(), lastIndex);
        }
      }
      index = segment.get().name().firstIndex() - 1;
    }
    if (!sawRecords) {
      return new Found.Behind(-1);
    }
    throw new NotInBucket(
        id.partitionId(),
        id.checkpointId(),
        lastIndex == -1 ? checkpointPosition : firstLogPosition.orElse(checkpointPosition),
        0);
  }

  /**
   * Writes the backup's log from the bucket into the target folder, as a journal named as the
   * partition's, and returns the backup with that journal's files as its segments.
   */
  private Backup withLogFromBucket(final Backup backup, final Path targetFolder)
      throws IOException, FlushException {
    final var id = backup.id();
    final var log = log(id.partitionId());
    final var key = ObjectNames.backupLog(log, id.checkpointId());
    final var backupLog =
        BackupLog.fromJson(
            store
                .get(key)
                .orElseThrow(() -> new NoBackupLog(id.partitionId(), id.checkpointId(), key)));
    final var path =
        Histories.load(store, cluster, tenant).path(backupLog.history(), log.partition());
    if (path.isEmpty()) {
      throw new NoHistory(log);
    }
    final var segments = new PathSegments(store, log, path);
    segments.refresh();
    final var name = RaftPartition.PARTITION_NAME_FORMAT.formatted(tenant, log.partition());
    final PathEntries.Appended appended;
    try (final var journal =
        SegmentedJournal.builder(new SimpleMeterRegistry())
            .withDirectory(targetFolder.toFile())
            .withName(name)
            .withMetaStore(new InMemory())
            .build()) {
      appended =
          new PathEntries(store, log, segments)
              .append(journal, backupLog.firstIndex(), backupLog.checkpointPosition());
      journal.flush();
    }
    LOG.info(
        "Restored the log of backup {} from the Event Plane's bucket: entries {} to {} of history {}",
        id,
        backupLog.firstIndex(),
        appended.lastIndex(),
        backupLog.history());
    final var files = new HashMap<String, Path>();
    try (final var listed = Files.list(targetFolder)) {
      listed
          .filter(
              file -> {
                final var fileName = file.getFileName().toString();
                return fileName.startsWith(name + "-") && fileName.endsWith(JOURNAL_SUFFIX);
              })
          .forEach(file -> files.put(file.getFileName().toString(), file));
    }
    return new BackupImpl(
        backup.id(), backup.descriptor(), backup.snapshot(), new NamedFileSetImpl(files));
  }

  private CompletableFuture<Void> deleteBackupLog(final BackupIdentifier id) {
    return CompletableFuture.runAsync(
        () ->
            unchecked(
                () ->
                    store.delete(ObjectNames.backupLog(log(id.partitionId()), id.checkpointId()))),
        executor);
  }

  private LogId log(final int partition) {
    return new LogId(cluster, tenant, partition);
  }

  private static void unchecked(final ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (final Exception e) {
      throw unchecked(e);
    }
  }

  private static <T> T unchecked(final ThrowingSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (final Exception e) {
      throw unchecked(e);
    }
  }

  private static RuntimeException unchecked(final Exception e) {
    if (e instanceof final IOException io) {
      return new UncheckedIOException(io);
    }
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    return e instanceof final RuntimeException runtime ? runtime : new CompletionException(e);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  /** The history a partition's leader publishes in. */
  @FunctionalInterface
  public interface PublishingHistory {

    /** For a store that saves no backups, such as the restore app's. */
    PublishingHistory NONE = partition -> Optional.empty();

    /** The partition's history, or empty until its publisher has started one. */
    Optional<UUID> of(int partition);
  }

  /** What the bucket holds of a backup's log. */
  private sealed interface Found {

    /** Where the log lies: the entries from the first to the last index of the history's path. */
    record Located(UUID history, long firstIndex, long lastIndex) implements Found {}

    /** The bucket doesn't reach the checkpoint yet, only this position, or -1 if none. */
    record Behind(long uploadedPosition) implements Found {}
  }
}
