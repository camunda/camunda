/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.retention;

import static io.camunda.zeebe.util.Unit.unit;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.client.api.BackupDeleteRequest;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *   <li><b>Retrieve Backups:</b> Fetches all existing backups for the partition from the backup
 *       store, sorted by creation time and checkpoint ID.
 *   <li><b>Filter Backups:</b> Identifies backups that fall outside the retention window (i.e.,
 *       backups older than {@code currentTime - retentionWindow}) and marks them for deletion.
 *   <li><b>Write Delete Commands:</b> For each deletable backup, sends a {@code DELETE_BACKUP}
 *       request to the partition leader via the {@link BrokerClient}. The leader's stream processor
 *       handles the actual deletion: updating the CHECKPOINTS and BACKUP_RANGES column families,
 *       asynchronously deleting from the backup store, and syncing the JSON metadata file.
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
 *   <li>Number of backups deleted
 * </ul>
 *
 * @see BackupStore
 * @see Schedule
 * @see BrokerClient
 */
public class BackupRetention extends Actor {
  private static final Logger LOG = LoggerFactory.getLogger(BackupRetention.class);
  private static final Comparator<BackupStatus> BACKUP_STATUS_COMPARATOR =
      Comparator.comparing(
          (BackupStatus s) -> {
            final var refTimestampOpt =
                s.descriptor().map(BackupDescriptor::checkpointTimestamp).or(s::lastModified);
            return refTimestampOpt.orElse(null);
          },
          Comparator.nullsLast(Comparator.naturalOrder()));
  private static final Comparator<BackupStatus> MAX_BACKUP_COMPARATOR =
      Comparator.comparing(
          status -> {
            if (status.created().isPresent()) {
              return status.created().get().toEpochMilli();
            } else if (status.lastModified().isPresent()) {
              return status.lastModified().get().toEpochMilli();
            } else {
              return status.id().checkpointId();
            }
          });

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
    final var partitionFutures =
        topologyManager.getTopology(physicalTenantId).getPartitions().stream()
            .parallel()
            .map(this::createRetentionContext)
            .map(
                future ->
                    future
                        .thenApply(this::logContext, this)
                        .thenApply(this::writeDeleteCommands, this))
            .collect(new ActorFutureCollector<>(this));

    partitionFutures.onComplete(
        (futures, error) -> {
          if (error != null) {
            retentionFuture.completeExceptionally(error);
          } else {
            retentionFuture.complete(unit());
          }
        });
    return retentionFuture;
  }

  private RetentionContext logContext(final RetentionContext ctx) {
    LOG.atDebug()
        .addKeyValue("deletableBackups", ctx.deletableBackups)
        .addKeyValue("earliestBackupInNewRange", ctx.earliestBackupInNewRange)
        .setMessage("Determined retention context for partition " + ctx.partitionId)
        .log();
    return ctx;
  }

  private ActorFuture<RetentionContext> createRetentionContext(final int partitionId) {
    return retrieveBackups(partitionId)
        .thenApply(this::excludeBackupsWithoutTimestamps, this)
        .thenApply((statuses) -> processBackups(statuses, partitionId), this);
  }

  private ActorFuture<Collection<BackupStatus>> retrieveBackups(final int partitionId) {
    final var identifier =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.any());
    final ActorFuture<Collection<BackupStatus>> requestFuture = createFuture();
    final var store = backupStore;
    if (store == null) {
      requestFuture.completeExceptionally(
          new IllegalStateException("backupStore must be initialized before retention runs"));
      return requestFuture;
    }
    store
        .list(identifier)
        .thenApplyAsync(
            backups -> backups.stream().sorted(BACKUP_STATUS_COMPARATOR).toList(), actor)
        .whenCompleteAsync(
            (backups, throwable) -> {
              if (throwable != null) {
                requestFuture.completeExceptionally(throwable);
              } else {
                requestFuture.complete(backups);
              }
            },
            actor);
    return requestFuture;
  }

  private RetentionContext processBackups(
      final Collection<BackupStatus> backups, final int partitionId) {

    final var latestCompletedBackup = latestCompletedBackup(backups);
    if (latestCompletedBackup.isEmpty()) {
      LOG.debug(
          "Unable to determine retention window for partition {}. No completed backup found.",
          partitionId);
      // Returning a context with no deletable backups will not trigger any further actions
      return RetentionContext.init(partitionId, List.of(), -1L, null);
    }

    long firstAvailableBackupInNewRange = -1L;
    final var deletableBackups = new ArrayList<BackupIdentifier>();

    final var windowBound = calculateWindowBound(latestCompletedBackup.get());

    for (final var backup : backups) {
      final var timestamp = backupTimestamp(backup);

      if (timestamp != null && windowBound != null && timestamp.isBefore(windowBound)) {
        if (backup.id().checkpointId() != latestCompletedBackup.get().id().checkpointId()) {
          deletableBackups.add(backup.id());
        } else {
          // If the backup is the latest completed backup it should not be deleted and the marker
          // should be moved to that backup id.
          firstAvailableBackupInNewRange = backup.id().checkpointId();
        }
      } else {
        // Only consider completed backups for the range change.
        if (backup.statusCode() == BackupStatusCode.COMPLETED
            && firstAvailableBackupInNewRange == -1L) {
          firstAvailableBackupInNewRange = backup.id().checkpointId();
        }
        if (firstAvailableBackupInNewRange == -1L) {
          continue;
        }
        break;
      }
    }
    return RetentionContext.init(
        partitionId, deletableBackups, firstAvailableBackupInNewRange, windowBound);
  }

  private Optional<BackupStatus> latestCompletedBackup(final Collection<BackupStatus> backups) {
    return backups.stream()
        .filter(f -> f.statusCode() == BackupStatusCode.COMPLETED)
        .max(MAX_BACKUP_COMPARATOR);
  }

  private Collection<BackupStatus> excludeBackupsWithoutTimestamps(
      final Collection<BackupStatus> backups) {
    return backups.stream().filter(backup -> backupTimestamp(backup) != null).toList();
  }

  private @Nullable Instant calculateWindowBound(final BackupStatus latestCompletedBackup) {
    final var completedTimestamp = backupTimestamp(latestCompletedBackup);
    if (completedTimestamp != null) {
      return completedTimestamp.minusSeconds(retentionWindow.toSeconds());
    } else {
      return null;
    }
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
  private CompletableActorFuture<Void> writeDeleteCommands(final RetentionContext context) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    if (context.deletableBackups.isEmpty()) {
      future.complete(unit());
      return future;
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

    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .thenAcceptAsync(
            ignore -> {
              metrics
                  .forPartition(context.partitionId)
                  .setBackupsDeleted(uniqueCheckpointIds.length);
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
            actor)
        .whenCompleteAsync(future, actor);
    return future;
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
