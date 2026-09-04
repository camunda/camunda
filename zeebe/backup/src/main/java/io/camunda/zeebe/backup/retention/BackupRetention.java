/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.retention;

import static io.camunda.zeebe.util.Unit.unit;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.ListOptions;
import io.camunda.zeebe.backup.client.api.BackupDeleteRequest;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the retention of backups by periodically identifying old backups and routing their
 * deletion through the stream processor via {@code DELETE_BACKUP} commands.
 *
 * <p>An instance is scoped to a single physical tenant: it only looks at that tenant's partitions
 * and only sends delete commands to them, so tenants with different backup stores and retention
 * windows are kept independent.
 *
 * <h2>Retention Process</h2>
 *
 * The retention process is executed on a configurable schedule and performs the following steps for
 * each partition of the physical tenant:
 *
 * <ol>
 *   <li><b>Find the anchor:</b> Reads backups newest first until the latest completed backup is
 *       found. Its timestamp minus the retention window is the window bound.
 *   <li><b>Sweep expired backups:</b> Reads backups oldest first, in batches. Every backup older
 *       than the window bound is deleted, except the anchor. The sweep stops at the first backup
 *       inside the window, so only the expired backups and one page at each end are ever read.
 *   <li><b>Write Delete Commands:</b> For each batch, sends a {@code DELETE_BACKUP} request per
 *       deletable checkpoint to the partition leader via the {@link BrokerClient}, and waits for
 *       them before reading the next batch. The leader's stream processor handles the actual
 *       deletion: updating the CHECKPOINTS and BACKUP_RANGES column families, asynchronously
 *       deleting from the backup store, and syncing the JSON metadata file.
 * </ol>
 *
 * Checkpoint ids are strictly increasing per partition, so reading in checkpoint id order is
 * reading in creation order. This keeps the cost of a retention run proportional to the number of
 * expired backups instead of the number of stored backups.
 *
 * <h2>Scheduling</h2>
 *
 * The retention task is scheduled according to the provided {@link Schedule}. After each execution
 * (successful or failed), the next execution time is calculated and the task is rescheduled.
 *
 * <h2>Metrics</h2>
 *
 * The following metrics are recorded during retention:
 *
 * <ul>
 *   <li>Next scheduled execution time
 *   <li>Last execution time
 *   <li>Earliest retained backup ID
 *   <li>Number of backups deleted
 * </ul>
 *
 * @see BackupStore
 * @see Schedule
 * @see BrokerClient
 */
public class BackupRetention extends Actor {
  private static final Logger LOG = LoggerFactory.getLogger(BackupRetention.class);

  /** Newest-first page size while looking for the latest completed backup. */
  private static final int ANCHOR_PAGE_SIZE = 20;

  /**
   * Oldest-first batch size while sweeping expired backups. Every batch enumerates the partition's
   * manifest keys again, so batches are large to keep that overhead small during a backlog.
   */
  private static final int SWEEP_BATCH_SIZE = 1000;

  private final String physicalTenantId;
  private final Supplier<BackupStore> backupStoreFactory;
  private final BrokerClient brokerClient;
  private final Schedule retentionSchedule;
  private final Duration retentionWindow;
  private final BrokerTopologyManager topologyManager;
  private final RetentionMetrics metrics;

  private @Nullable BackupStore backupStore;

  public BackupRetention(
      final String physicalTenantId,
      final Supplier<BackupStore> backupStoreFactory,
      final BrokerClient brokerClient,
      final Schedule retentionSchedule,
      final Duration retentionWindow,
      final BrokerTopologyManager topologyManager,
      final MeterRegistry meterRegistry) {
    super("BackupRetention", null, Map.of(ACTOR_PROP_PHYSICAL_TENANT, physicalTenantId));
    this.physicalTenantId = physicalTenantId;
    metrics = new RetentionMetrics(meterRegistry);
    this.backupStoreFactory = backupStoreFactory;
    this.brokerClient = brokerClient;
    this.retentionSchedule = retentionSchedule;
    this.retentionWindow = retentionWindow;
    this.topologyManager = topologyManager;
  }

  @Override
  protected void onActorStarted() {
    LOG.info("Backup retention initialized with cleanup schedule {}", retentionSchedule);
    backupStore = backupStoreFactory.get();
    metrics.register();
    scheduleNextRetention();
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Retention scheduler stopped");
    metrics.close();
    if (backupStore != null) {
      // Initiated but not awaited because it's a resource leak only, not a fatal error.
      backupStore.closeAsync();
      backupStore = null;
    }
  }

  private void reschedulingTask() {
    if (topologyManager.isRecovering(physicalTenantId)) {
      LOG.debug(
          "Skipping backup retention, physical tenant {} is in recovery mode", physicalTenantId);
      scheduleNextRetention();
      return;
    }

    performRetention()
        .onComplete(
            (v, err) -> {
              metrics.recordLastExecution(Instant.ofEpochMilli(ActorClock.currentTimeMillis()));
              if (err != null) {
                LOG.error("Unexpected error occurred during backup retention task", err);
              } else {
                LOG.debug("Backup retention task completed successfully");
              }
              scheduleNextRetention();
            });
  }

  private void scheduleNextRetention() {
    final var next = retentionSchedule.nextExecution(ActorClock.currentInstant());
    LOG.debug("Scheduling next retention task in {} ", next);
    metrics.recordNextExecution(next.get());
    actor.runAt(next.get().toEpochMilli(), this::reschedulingTask);
  }

  private ActorFuture<Void> performRetention() {
    final ActorFuture<Void> retentionFuture = createFuture();
    final var store = backupStore;
    if (store == null) {
      retentionFuture.completeExceptionally(
          new IllegalStateException("backupStore must be initialized before retention runs"));
      return retentionFuture;
    }

    final var partitionRetentions =
        topologyManager.getTopology(physicalTenantId).getPartitions().stream()
            .map(partitionId -> retainPartition(store, partitionId))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(partitionRetentions)
        .whenCompleteAsync(
            (ignored, error) -> {
              if (error != null) {
                retentionFuture.completeExceptionally(error);
              } else {
                retentionFuture.complete(unit());
              }
            },
            actor);
    return retentionFuture;
  }

  /**
   * Deletes the expired backups of one partition. Finds the latest completed backup newest first,
   * then sweeps oldest first until the first backup inside the retention window.
   */
  private CompletableFuture<Void> retainPartition(final BackupStore store, final int partitionId) {
    return findLatestCompletedBackup(store, partitionId, OptionalLong.empty())
        .thenComposeAsync(
            anchor -> {
              if (anchor.isEmpty()) {
                LOG.debug(
                    "Unable to determine retention window for partition {}. No completed backup found.",
                    partitionId);
                return CompletableFuture.<Void>completedFuture(null);
              }
              final var windowBound = calculateWindowBound(anchor.get());
              return sweep(
                  store,
                  new PartitionSweep(partitionId, anchor.get(), windowBound),
                  OptionalLong.empty());
            },
            actor);
  }

  /**
   * Reads pages newest first until one holds a completed backup with a timestamp. Usually that is
   * the first page. Returns empty when the whole partition holds no such backup.
   */
  private CompletableFuture<Optional<BackupStatus>> findLatestCompletedBackup(
      final BackupStore store, final int partitionId, final OptionalLong before) {
    return store
        .list(
            allBackupsOfPartition(partitionId),
            ListOptions.newestFirst(before, OptionalInt.of(ANCHOR_PAGE_SIZE)))
        .thenComposeAsync(
            page -> {
              final var latestCompleted =
                  page.stream()
                      .filter(backup -> backup.statusCode() == BackupStatusCode.COMPLETED)
                      .filter(backup -> backupTimestamp(backup) != null)
                      .max(Comparator.comparingLong(backup -> backup.id().checkpointId()));
              if (latestCompleted.isPresent() || isLastPage(page, ANCHOR_PAGE_SIZE)) {
                return CompletableFuture.completedFuture(latestCompleted);
              }
              return findLatestCompletedBackup(
                  store, partitionId, OptionalLong.of(oldestCheckpointId(page)));
            },
            actor);
  }

  /**
   * Reads one batch oldest first, deletes its expired backups and continues with the next batch
   * until the first retained completed backup is seen or the partition is exhausted.
   */
  private CompletableFuture<Void> sweep(
      final BackupStore store, final PartitionSweep sweep, final OptionalLong after) {
    return store
        .list(
            allBackupsOfPartition(sweep.partitionId),
            ListOptions.oldestFirst(after, OptionalInt.of(SWEEP_BATCH_SIZE)))
        .thenComposeAsync(
            batch -> {
              final var result = processBatch(batch, sweep);
              logContext(result.context());
              return writeDeleteCommands(result.context(), sweep)
                  .thenComposeAsync(
                      ignored -> {
                        if (result.reachedWindow() || isLastPage(batch, SWEEP_BATCH_SIZE)) {
                          return CompletableFuture.<Void>completedFuture(null);
                        }
                        return sweep(store, sweep, OptionalLong.of(newestCheckpointId(batch)));
                      },
                      actor);
            },
            actor);
  }

  /**
   * Walks a batch in checkpoint id order. Every backup with a timestamp before the window bound is
   * deletable, except the anchor. The first completed backup at or after the bound is the earliest
   * backup of the new range and ends the sweep. Backups without a timestamp are skipped.
   */
  private BatchResult processBatch(final List<BackupStatus> batch, final PartitionSweep sweep) {
    final var deletableBackups = new ArrayList<BackupIdentifier>();
    long earliestBackupInNewRange = -1L;
    boolean reachedWindow = false;

    for (final var backup : batch) {
      final var timestamp = backupTimestamp(backup);
      if (timestamp == null) {
        continue;
      }

      if (timestamp.isBefore(sweep.windowBound)) {
        if (backup.id().checkpointId() != sweep.anchor.id().checkpointId()) {
          deletableBackups.add(backup.id());
        } else {
          // If the backup is the latest completed backup it should not be deleted and the marker
          // should be moved to that backup id.
          earliestBackupInNewRange = backup.id().checkpointId();
        }
      } else {
        // Only consider completed backups for the range change.
        if (backup.statusCode() == BackupStatusCode.COMPLETED && earliestBackupInNewRange == -1L) {
          earliestBackupInNewRange = backup.id().checkpointId();
        }
        if (earliestBackupInNewRange == -1L) {
          continue;
        }
        reachedWindow = true;
        break;
      }
    }
    return new BatchResult(
        RetentionContext.init(
            sweep.partitionId, deletableBackups, earliestBackupInNewRange, sweep.windowBound),
        reachedWindow);
  }

  private void logContext(final RetentionContext ctx) {
    LOG.atDebug()
        .addKeyValue("deletableBackups", ctx.deletableBackups)
        .addKeyValue("earliestBackupInNewRange", ctx.earliestBackupInNewRange)
        .setMessage("Determined retention context for partition " + ctx.partitionId)
        .log();
  }

  private static boolean isLastPage(final Collection<BackupStatus> page, final int limit) {
    return page.stream().map(backup -> backup.id().checkpointId()).distinct().count() < limit;
  }

  private static long oldestCheckpointId(final Collection<BackupStatus> page) {
    return page.stream().mapToLong(backup -> backup.id().checkpointId()).min().orElseThrow();
  }

  private static long newestCheckpointId(final Collection<BackupStatus> page) {
    return page.stream().mapToLong(backup -> backup.id().checkpointId()).max().orElseThrow();
  }

  private static BackupIdentifierWildcardImpl allBackupsOfPartition(final int partitionId) {
    return new BackupIdentifierWildcardImpl(
        Optional.empty(), Optional.of(partitionId), CheckpointPattern.any());
  }

  private Instant calculateWindowBound(final BackupStatus latestCompletedBackup) {
    final var completedTimestamp =
        requireNonNull(
            backupTimestamp(latestCompletedBackup), "anchor backup must have a timestamp");
    return completedTimestamp.minusSeconds(retentionWindow.toSeconds());
  }

  /**
   * Sends a {@code DELETE_BACKUP} request to the partition leader for each deletable backup. The
   * leader's stream processor handles the actual deletion: updating the CHECKPOINTS and
   * BACKUP_RANGES column families, asynchronously deleting from the backup store, and syncing the
   * JSON metadata file.
   *
   * <p>Multiple backup copies (from different broker nodes) for the same checkpoint ID are handled
   * by a single {@code DELETE_BACKUP} command — the stream processor's post-commit task deletes all
   * copies via a wildcard query.
   */
  private CompletableFuture<Void> writeDeleteCommands(
      final RetentionContext context, final PartitionSweep sweep) {
    if (context.deletableBackups.isEmpty()) {
      if (context.earliestBackupInNewRange > 0) {
        metrics
            .forPartition(context.partitionId)
            .setEarliestBackupId(context.earliestBackupInNewRange);
      }
      return CompletableFuture.completedFuture(null);
    }

    // Deduplicate by checkpoint ID — a single DELETE_BACKUP command handles all node copies
    final var uniqueCheckpointIds =
        context.deletableBackups.stream()
            .mapToLong(BackupIdentifier::checkpointId)
            .distinct()
            .toArray();

    LOG.debug(
        "Sending {} DELETE_BACKUP commands for partition {}",
        uniqueCheckpointIds.length,
        context.partitionId);

    final var futures = new ArrayList<CompletableFuture<?>>(uniqueCheckpointIds.length);
    for (final var checkpointId : uniqueCheckpointIds) {
      final var request = new BackupDeleteRequest();
      request.setPartitionGroup(physicalTenantId);
      request.setPartitionId(context.partitionId);
      request.setBackupId(checkpointId);
      futures.add(
          brokerClient
              .sendRequestWithRetry(request)
              .thenAcceptAsync(this::throwOnBrokerError, actor));
    }

    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .thenAcceptAsync(
            ignore -> {
              sweep.deleted += uniqueCheckpointIds.length;
              metrics.forPartition(context.partitionId).setBackupsDeleted(sweep.deleted);
              if (context.earliestBackupInNewRange > 0) {
                metrics
                    .forPartition(context.partitionId)
                    .setEarliestBackupId(context.earliestBackupInNewRange);
              }
            },
            actor)
        .whenCompleteAsync(
            (result, error) -> {
              if (error != null) {
                LOG.error(
                    "Failed to send DELETE_BACKUP commands for partition {}",
                    context.partitionId,
                    error);
              }
            },
            actor);
  }

  private void throwOnBrokerError(final BrokerResponse<?> response) {
    if (!response.isResponse()) {
      throw response.toException();
    }
  }

  // `@Nullable` had to be added manually:
  // NullAway cannot infer that `orElseGet(() -> null)` is nullable
  private @Nullable Instant backupTimestamp(final BackupStatus backup) {
    return backup
        .descriptor()
        .map(BackupDescriptor::checkpointTimestamp)
        .or(backup::created)
        .or(backup::lastModified)
        .orElseGet(
            () -> {
              LOG.debug("Unable to determine timestamp for backup {}.", backup.id());
              return null;
            });
  }

  /** The state of one partition's sweep: the anchor, the window it defines and what was deleted. */
  private static final class PartitionSweep {
    private final int partitionId;
    private final BackupStatus anchor;
    private final Instant windowBound;
    private int deleted;

    private PartitionSweep(
        final int partitionId, final BackupStatus anchor, final Instant windowBound) {
      this.partitionId = partitionId;
      this.anchor = anchor;
      this.windowBound = windowBound;
    }
  }

  private record BatchResult(RetentionContext context, boolean reachedWindow) {}

  record RetentionContext(
      List<BackupIdentifier> deletableBackups,
      long earliestBackupInNewRange,
      int partitionId,
      @Nullable Instant windowBoundary) {

    static RetentionContext init(
        final int partitionId,
        final List<BackupIdentifier> deletableBackups,
        final long earliestBackupInNewRange,
        final @Nullable Instant windowBoundary) {
      return new RetentionContext(
          deletableBackups, earliestBackupInNewRange, partitionId, windowBoundary);
    }
  }
}
