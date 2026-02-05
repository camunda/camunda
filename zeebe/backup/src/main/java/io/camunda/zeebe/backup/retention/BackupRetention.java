/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.retention;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupRangeMarker;
import io.camunda.zeebe.backup.api.BackupRanges;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the retention of backups by periodically deleting old backups and their associated range
 * markers based on a configurable retention window.
 *
 * <h2>Retention Process</h2>
 *
 * The retention process is executed on a configurable schedule and performs the following steps for
 * each partition:
 *
 * <ol>
 *   <li><b>Retrieve Backups:</b> Fetches all existing backups for the partition from the backup
 *       store, sorted by creation time and checkpoint ID.
 *   <li><b>Filter Backups:</b> Identifies backups that fall outside the retention window (i.e.,
 *       backups older than {@code currentTime - retentionWindow}) and marks them for deletion.
 *       Determines the earliest backup checkpoint ID that should be retained.
 *   <li><b>Retrieve Range Markers:</b> Fetches all range markers for the partition and enriches the
 *       retention context. Range markers with checkpoint IDs less than the earliest retained backup
 *       are marked for deletion.
 *   <li><b>Reset Range Start:</b> If a previous start marker exists and its associated end marker
 *       has a lower checkpoint ID, the start marker is deleted and a new one is created pointing to
 *       the earliest retained backup.
 *   <li><b>Delete Markers:</b> Removes all range markers that are no longer needed (those
 *       associated with deleted backups).
 *   <li><b>Delete Backups:</b> Removes all backups identified for deletion from the backup store.
 * </ol>
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
 *   <li>Number of range markers deleted
 *   <li>Number of backups deleted
 * </ul>
 *
 * @see BackupStore
 * @see Schedule
 * @see BackupRangeMarker
 */
public class BackupRetention extends Actor {
  private static final Logger LOG = LoggerFactory.getLogger(BackupRetention.class);
  private static final Comparator<BackupStatus> BACKUP_STATUS_COMPARATOR =
      Comparator.comparing(
          (BackupStatus s) ->
              s.descriptor()
                  .map(BackupDescriptor::checkpointTimestamp)
                  .orElse(Instant.ofEpochMilli(s.id().checkpointId())),
          Comparator.nullsLast(Comparator.naturalOrder()));

  private final BackupStore backupStore;
  private final Schedule retentionSchedule;
  private final Duration retentionWindow;
  private final long checkpointOffset;
  private final Supplier<Integer> partitionsSupplier;
  private final RetentionMetrics metrics;

  public BackupRetention(
      final BackupStore backupStore,
      final Schedule retentionSchedule,
      final Duration retentionWindow,
      final long checkpointOffset,
      final Supplier<Integer> partitionsSupplier,
      final MeterRegistry meterRegistry) {
    metrics = new RetentionMetrics(meterRegistry);
    this.backupStore = backupStore;
    this.retentionSchedule = retentionSchedule;
    this.retentionWindow = retentionWindow;
    this.checkpointOffset = checkpointOffset;
    this.partitionsSupplier = partitionsSupplier;
  }

  @Override
  protected void onActorStarted() {
    LOG.debug("Retention scheduler started");
    metrics.register();
    final var next =
        retentionSchedule.nextExecution(Instant.ofEpochMilli(ActorClock.currentTimeMillis()));
    LOG.debug("Scheduling next retention task in {} ", next);
    metrics.recordNextExecution(next.get());
    actor.runAt(next.get().toEpochMilli(), this::reschedulingTask);
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Retention scheduler stopped");
    metrics.close();
  }

  private void reschedulingTask() {
    performRetention()
        .onComplete(
            (v, err) -> {
              metrics.recordLastExecution(Instant.ofEpochMilli(ActorClock.currentTimeMillis()));
              if (err != null) {
                LOG.error("Unexpected error occurred during backup retention task", err);
              } else {
                LOG.debug("Backup retention task completed successfully");
              }
              final var next =
                  retentionSchedule.nextExecution(
                      Instant.ofEpochMilli(ActorClock.currentTimeMillis()));
              LOG.debug("Scheduling next retention task in {} ", next);
              metrics.recordNextExecution(next.get());
              actor.runAt(next.get().toEpochMilli(), this::reschedulingTask);
            });
  }

  private ActorFuture<Void> performRetention() {
    final var now = ActorClock.currentTimeMillis();
    final var window = now - retentionWindow.toMillis();
    final ActorFuture<Void> retentionFuture = createFuture();
    final var partitionFutures =
        IntStream.rangeClosed(1, partitionsSupplier.get())
            .parallel()
            .mapToObj(partitionId -> createRetentionContext(partitionId, window))
            .map(
                future ->
                    future
                        .andThen(this::logContext, this)
                        .andThen(this::resetRangeStart, this)
                        .andThen(this::deleteMarkers, this)
                        .thenApply(this::deleteBackups, this)
                        .toCompletableFuture())
            .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(partitionFutures).thenAccept(v -> retentionFuture.complete(null));

    return retentionFuture;
  }

  private ActorFuture<RetentionContext> logContext(final RetentionContext ctx) {
    LOG.atDebug()
        .addKeyValue("deletableBackups", ctx.deletableBackups)
        .addKeyValue("earliestBackupInNewRange", ctx.earliestBackupInNewRange)
        .addKeyValue("previousStartMarker", ctx.previousStartMarker)
        .addKeyValue("deletableRangeMarkers", ctx.deletableRangeMarkers)
        .setMessage("Retention context for partition " + ctx.partitionId)
        .log();
    return CompletableActorFuture.completed(ctx);
  }

  private ActorFuture<RetentionContext> createRetentionContext(
      final int partitionId, final long window) {
    return retrieveBackups(partitionId)
        .thenApply((statuses) -> filterBackups(statuses, window, partitionId), this)
        .andThen(
            (context) ->
                retrieveRangeMarkers(partitionId)
                    .thenApply((markers) -> enrichContextWithMarkers(context, markers), this),
            this);
  }

  private ActorFuture<Collection<BackupStatus>> retrieveBackups(final int partitionId) {
    final var identifier =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.any());
    final ActorFuture<Collection<BackupStatus>> requestFuture = createFuture();
    backupStore
        .list(identifier)
        .thenApply(backups -> backups.stream().sorted(BACKUP_STATUS_COMPARATOR).toList())
        .thenAccept(requestFuture::complete);
    return requestFuture;
  }

  private ActorFuture<Collection<BackupRangeMarker>> retrieveRangeMarkers(final int partitionId) {
    final ActorFuture<Collection<BackupRangeMarker>> requestFuture = createFuture();
    backupStore
        .rangeMarkers(partitionId)
        .thenApply(markers -> markers.stream().sorted(BackupRanges.MARKER_ORDERING).toList())
        .thenAccept(requestFuture::complete);
    return requestFuture;
  }

  private RetentionContext enrichContextWithMarkers(
      final RetentionContext retentionContext, final Collection<BackupRangeMarker> markers) {
    BackupRangeMarker rangeStart = null;

    for (final BackupRangeMarker marker : markers) {
      if (marker instanceof BackupRangeMarker.Start
          && marker.checkpointId() <= retentionContext.earliestBackupInNewRange) {
        rangeStart = marker;
      }

      if (marker.checkpointId() < retentionContext.earliestBackupInNewRange) {
        retentionContext.deletableRangeMarkers.add(marker);
      } else {
        break;
      }
    }
    if (rangeStart != null) {
      return retentionContext.withPreviousStartMarker(rangeStart);
    }
    return retentionContext;
  }

  private RetentionContext filterBackups(
      final Collection<BackupStatus> backups, final long window, final int partitionId) {
    final var context = RetentionContext.init(partitionId);
    long firstAvailableBackupInNewRange = -1L;

    for (final var backup : backups) {
      final var backupTimestamp =
          backup
              .descriptor()
              .map(BackupDescriptor::checkpointTimestamp)
              .orElse(Instant.ofEpochMilli(backup.id().checkpointId() - checkpointOffset))
              .toEpochMilli();
      if (backupTimestamp < window) {
        context.deletableBackups.add(backup.id());
      } else {
        firstAvailableBackupInNewRange = backup.id().checkpointId();
        break;
      }
    }
    return context.withEarliestBackupInNewRange(firstAvailableBackupInNewRange);
  }

  private CompletableActorFuture<RetentionContext> resetRangeStart(final RetentionContext context) {
    final CompletableActorFuture<RetentionContext> future = new CompletableActorFuture<>();
    metrics.recordEarliestBackupId(context.earliestBackupInNewRange);
    if (shouldResetMarker(context)) {
      final var marker = context.previousStartMarker.get();
      LOG.debug(
          "Resetting range start marker {} for partition {} to checkpoint id {}",
          context.previousStartMarker.get(),
          context.partitionId,
          context.earliestBackupInNewRange);
      backupStore
          .storeRangeMarker(
              context.partitionId, new BackupRangeMarker.Start(context.earliestBackupInNewRange))
          .thenCompose(ignore -> backupStore.deleteRangeMarker(context.partitionId, marker))
          .thenAccept(ignore -> metrics.recordEarliestBackupId(context.earliestBackupInNewRange))
          .thenApply(v -> context)
          .thenAccept(future::complete);
    } else {
      future.complete(context);
    }
    return future;
  }

  private boolean shouldResetMarker(final RetentionContext context) {
    return context.previousStartMarker.isPresent()
        && !context.deletableBackups.isEmpty()
        && context.previousStartMarker.get().checkpointId() != context.earliestBackupInNewRange;
  }

  private CompletableActorFuture<RetentionContext> deleteMarkers(final RetentionContext context) {
    final CompletableActorFuture<RetentionContext> future = new CompletableActorFuture<>();
    if (context.deletableRangeMarkers.isEmpty() || context.deletableBackups.isEmpty()) {
      future.complete(context);
      return future;
    }
    LOG.debug(
        "Deleting range markers {} for partition {}",
        context.deletableRangeMarkers,
        context.partitionId);

    final var futures =
        context.deletableRangeMarkers.stream()
            .map(marker -> backupStore.deleteRangeMarker(context.partitionId, marker))
            .toList()
            .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures)
        .thenAccept(ignore -> metrics.recordRangesDeleted(context.deletableRangeMarkers.size()))
        .thenApply(v -> context)
        .thenAccept(future::complete);
    return future;
  }

  private CompletableActorFuture<Void> deleteBackups(final RetentionContext context) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    if (context.deletableBackups.isEmpty()) {
      future.complete(null);
      return future;
    }
    LOG.debug(
        "Deleting {} backups for partition {}", context.deletableBackups, context.partitionId);
    final var futures =
        context.deletableBackups.stream()
            .map(backupStore::delete)
            .toList()
            .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures)
        .thenAccept(ignore -> metrics.recordBackupsDeleted(context.deletableBackups().size()))
        .thenAccept(future::complete);
    return future;
  }

  record RetentionContext(
      List<BackupIdentifier> deletableBackups,
      long earliestBackupInNewRange,
      Optional<BackupRangeMarker> previousStartMarker,
      List<BackupRangeMarker> deletableRangeMarkers,
      int partitionId) {

    static RetentionContext init(final int partitionId) {
      return new RetentionContext(
          new ArrayList<>(), -1L, Optional.empty(), new ArrayList<>(), partitionId);
    }

    RetentionContext withPreviousStartMarker(final BackupRangeMarker rangeMarker) {
      return new RetentionContext(
          deletableBackups,
          earliestBackupInNewRange,
          Optional.of(rangeMarker),
          deletableRangeMarkers,
          partitionId);
    }

    RetentionContext withEarliestBackupInNewRange(final long checkpointId) {
      return new RetentionContext(
          deletableBackups, checkpointId, previousStartMarker, deletableRangeMarkers, partitionId);
    }
  }
}
