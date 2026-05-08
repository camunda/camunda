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
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.client.api.BackupDeleteRequest;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the retention of backups by periodically identifying old backups and routing their
 * deletion through the stream processor via {@code DELETE_BACKUP} commands.
 *
 * <h2>Retention Process</h2>
 *
 * The retention process is triggered by the BPMN {@code retention_cleanup_scheduler} process and
 * performs the following steps for each partition:
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
 * <p>The pipeline is built from {@link CompletableFuture} stages — no actor is involved.
 * Continuations execute on the thread that completes the upstream future (storage client executor
 * for the listing stages, broker client executor for the delete-command stages).
 *
 * @see BackupStore
 * @see Schedule
 * @see BrokerClient
 */
public class BackupRetention {
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

  private final BackupStore backupStore;
  private final BrokerClient brokerClient;
  private final Schedule retentionSchedule;
  private final Duration retentionWindow;
  private final BrokerTopologyManager topologyManager;
  private final RetentionMetrics metrics;
  private final AtomicReference<CompletableFuture<List<Long>>> inFlight = new AtomicReference<>();
  private volatile boolean closed;

  public BackupRetention(
      final BackupStore backupStore,
      final BrokerClient brokerClient,
      final Schedule retentionSchedule,
      final Duration retentionWindow,
      final BrokerTopologyManager topologyManager,
      final MeterRegistry meterRegistry) {
    metrics = new RetentionMetrics(meterRegistry);
    this.backupStore = backupStore;
    this.brokerClient = brokerClient;
    this.retentionSchedule = retentionSchedule;
    this.retentionWindow = retentionWindow;
    this.topologyManager = topologyManager;
  }

  public void start() {
    LOG.debug("Retention scheduler started");
    metrics.register();
    closed = false;
  }

  public void close() {
    LOG.debug("Retention scheduler stopped");
    metrics.close();
    closed = true;
  }

  public boolean isClosed() {
    return closed;
  }

  /**
   * Triggers a single retention pass. If a previous pass is still running, the returned future is
   * bridged to it — concurrent invocations from BPMN job re-activations share one in-flight pass.
   * Returns the list of unique checkpoint IDs whose {@code DELETE_BACKUP} commands were submitted
   * on this pass.
   */
  public CompletableFuture<List<Long>> triggerRetention() {
    return inFlight.updateAndGet(prev -> (prev != null && !prev.isDone()) ? prev : runOnce());
  }

  private CompletableFuture<List<Long>> runOnce() {
    final var partitions = topologyManager.getTopology().getPartitions();
    @SuppressWarnings("unchecked")
    final CompletableFuture<List<Long>>[] perPartition =
        partitions.stream().map(this::retentionForPartition).toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(perPartition)
        .thenApply(
            v -> {
              final var aggregated = new ArrayList<Long>();
              for (final var f : perPartition) {
                aggregated.addAll(f.join());
              }
              return (List<Long>) aggregated;
            })
        .whenComplete((result, error) -> metrics.recordLastExecution(Instant.now()));
  }

  private CompletableFuture<List<Long>> retentionForPartition(final int partitionId) {
    final var identifier =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.any());
    return backupStore
        .list(identifier)
        .thenApply(this::excludeBackupsWithoutTimestamps)
        .thenApply(backups -> backups.stream().sorted(BACKUP_STATUS_COMPARATOR).toList())
        .thenApply(backups -> processBackups(backups, partitionId))
        .thenApply(this::logContext)
        .thenCompose(this::writeDeleteCommands);
  }

  private RetentionContext logContext(final RetentionContext ctx) {
    LOG.atDebug()
        .addKeyValue("deletableBackups", ctx.deletableBackups)
        .addKeyValue("earliestBackupInNewRange", ctx.earliestBackupInNewRange)
        .setMessage("Determined retention context for partition " + ctx.partitionId)
        .log();
    return ctx;
  }

  private RetentionContext processBackups(
      final Collection<BackupStatus> backups, final int partitionId) {

    final var latestCompletedBackup = latestCompletedBackup(backups);
    if (latestCompletedBackup.isEmpty()) {
      LOG.debug(
          "Unable to determine retention window for partition {}. No completed backup found.",
          partitionId);
      return RetentionContext.init(partitionId, List.of(), -1L, null);
    }

    long firstAvailableBackupInNewRange = -1L;
    final var deletableBackups = new ArrayList<BackupIdentifier>();

    final Instant windowBound = calculateWindowBound(latestCompletedBackup.get());

    for (final var backup : backups) {
      final var timestamp = backupTimestamp(backup);

      if (timestamp.isBefore(windowBound)) {
        if (backup.id().checkpointId() != latestCompletedBackup.get().id().checkpointId()) {
          deletableBackups.add(backup.id());
        } else {
          firstAvailableBackupInNewRange = backup.id().checkpointId();
        }
      } else {
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

  private Instant calculateWindowBound(final BackupStatus latestCompletedBackup) {
    return backupTimestamp(latestCompletedBackup).minusSeconds(retentionWindow.toSeconds());
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
  private CompletableFuture<List<Long>> writeDeleteCommands(final RetentionContext context) {
    if (context.deletableBackups.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }

    final var uniqueCheckpointIds =
        context.deletableBackups.stream()
            .mapToLong(BackupIdentifier::checkpointId)
            .distinct()
            .toArray();
    final List<Long> deletedIds = Arrays.stream(uniqueCheckpointIds).boxed().toList();

    LOG.debug(
        "Sending {} DELETE_BACKUP commands for partition {}",
        uniqueCheckpointIds.length,
        context.partitionId);

    final var sends = new CompletableFuture<?>[uniqueCheckpointIds.length];
    for (int i = 0; i < uniqueCheckpointIds.length; i++) {
      final var request = new BackupDeleteRequest();
      request.setPartitionId(context.partitionId);
      request.setBackupId(uniqueCheckpointIds[i]);
      sends[i] = brokerClient.sendRequestWithRetry(request);
    }

    return CompletableFuture.allOf(sends)
        .thenApply(
            v -> {
              metrics
                  .forPartition(context.partitionId)
                  .setBackupsDeleted(uniqueCheckpointIds.length);
              if (context.earliestBackupInNewRange > 0) {
                metrics
                    .forPartition(context.partitionId)
                    .setEarliestBackupId(context.earliestBackupInNewRange);
              }
              return deletedIds;
            });
  }

  private Instant backupTimestamp(final BackupStatus backup) {
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
      Instant windowBoundary) {

    static RetentionContext init(
        final int partitionId,
        final List<BackupIdentifier> deletableBackups,
        final long earliestBackupInNewRange,
        final Instant windowBoundary) {
      return new RetentionContext(
          deletableBackups, earliestBackupInNewRange, partitionId, windowBoundary);
    }
  }
}
