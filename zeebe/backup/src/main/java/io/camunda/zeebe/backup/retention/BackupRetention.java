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
import java.util.Objects;
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
 * The retention process is executed on a configurable schedule and performs the following steps:
 *
 * <ol>
 *   <li><b>Find the shared anchor:</b> The tenant can only be restored from a checkpoint that is
 *       completed on every one of its partitions. Partitions can complete checkpoints at different
 *       speeds, or fail them, so each partition's own latest completed backup is not necessarily
 *       such a checkpoint. The anchor is therefore the newest checkpoint id completed on every
 *       partition, found by reading each partition newest first. The earliest timestamp of the
 *       anchor across partitions minus the retention window is the window bound, shared by all
 *       partitions. If any partition cannot be read or no such checkpoint exists, nothing is
 *       deleted on any partition.
 *   <li><b>Sweep expired backups:</b> For each partition, reads backups oldest first, in batches.
 *       Every backup older than the window bound and than the anchor is deleted. The sweep stops at
 *       the first backup inside the window or at the anchor, so only the expired backups and one
 *       page at each end are ever read.
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

    final var partitions =
        List.copyOf(topologyManager.getTopology(physicalTenantId).getPartitions());

    findSharedAnchor(store, partitions)
        .thenComposeAsync(
            sharedAnchor ->
                sharedAnchor
                    .map(anchor -> sweepFutures(store, partitions, anchor))
                    .orElse(CompletableFuture.completedFuture(null)),
            actor)
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
   * Sweeps every partition using the given shared anchor, or does nothing when there is no anchor.
   */
  private CompletableFuture<Void> sweepFutures(
      final BackupStore store, final List<Integer> partitions, final SharedAnchor anchor) {
    return CompletableFuture.allOf(
        partitions.stream()
            .map(
                partitionId ->
                    sweep(store, new PartitionSweep(partitionId, anchor), OptionalLong.empty()))
            .toArray(CompletableFuture[]::new));
  }

  /**
   * Finds the newest checkpoint id that is completed on every given partition, or empty when there
   * is none.
   *
   * <p>Visits the partitions one at a time, round robin, asking each for its latest completed
   * backup at or below the current candidate. A lower answer becomes the new candidate. The
   * candidate is the anchor once every partition in a row has answered with it; it is gone once a
   * partition has no completed backup at or below it. The candidate only decreases, so the search
   * ends. When the partitions already agree, each one is asked exactly once.
   */
  private CompletableFuture<Optional<SharedAnchor>> findSharedAnchor(
      final BackupStore store, final List<Integer> partitions) {
    final var search = new SharedAnchorSearch(store, partitions);
    if (partitions.isEmpty()) {
      search.result.complete(Optional.empty());
    } else {
      search.nextPartition();
    }
    return search.result;
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
              logBatch(result, sweep);
              return writeDeleteCommands(result, sweep)
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
   * Walks a batch in checkpoint id order. Every backup with a timestamp before the window bound and
   * a checkpoint id below the anchor is deletable; the first completed backup that is kept is the
   * earliest backup of the new range. Backups without a timestamp are skipped. Checkpoints at or
   * above the anchor are never deleted, so a skewed timestamp cannot remove a restorable one.
   *
   * <p>Every entry is classified independently — the loop never stops partway through a batch on
   * the first one found at or after the bound. Record timestamps are the writing leader's wall
   * clock with no cross-leader monotonicity, so stopping there would let one clock-skewed or
   * corrupted timestamp on a low checkpoint id strand every genuinely expired backup above it,
   * forever, since the next run re-reads the same batch the same way. The batch is already bounded
   * to {@link #SWEEP_BATCH_SIZE}, so walking all of it costs nothing extra.
   */
  private BatchResult processBatch(final List<BackupStatus> batch, final PartitionSweep sweep) {
    final var deletableBackups = new ArrayList<BackupIdentifier>();
    long earliestBackupInNewRange = -1L;

    for (final var backup : batch) {
      final var timestamp = backupTimestamp(backup);
      if (timestamp == null) {
        continue;
      }

      if (timestamp.isBefore(sweep.anchor.windowBound)
          && backup.id().checkpointId() < sweep.anchor.checkpointId) {
        deletableBackups.add(backup.id());
      } else if (backup.statusCode() == BackupStatusCode.COMPLETED
          // Only the first completed backup that is kept, in checkpoint id order, marks the start
          // of the new range.
          && earliestBackupInNewRange == -1L) {
        earliestBackupInNewRange = backup.id().checkpointId();
      }
    }
    // Paging has caught up to the retained range once the batch's newest entry (the batch is
    // oldest-first) is itself at or after the bound, or has reached the anchor — everything beyond
    // it is kept. A single skewed or corrupted timestamp earlier in the batch no longer decides
    // this.
    final var reachedWindow =
        !batch.isEmpty()
            && (isAtOrAfterWindow(batch.getLast(), sweep.anchor.windowBound)
                || batch.getLast().id().checkpointId() >= sweep.anchor.checkpointId);
    return new BatchResult(deletableBackups, earliestBackupInNewRange, reachedWindow);
  }

  private boolean isAtOrAfterWindow(final BackupStatus backup, final Instant windowBound) {
    final var timestamp = backupTimestamp(backup);
    return timestamp != null && !timestamp.isBefore(windowBound);
  }

  private void logBatch(final BatchResult batch, final PartitionSweep sweep) {
    LOG.atDebug()
        .addKeyValue("deletableBackups", batch.deletableBackups)
        .addKeyValue("earliestBackupInNewRange", batch.earliestBackupInNewRange)
        .setMessage("Determined retention context for partition " + sweep.partitionId)
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

  /**
   * Each partition records its own timestamp for the anchor; the earliest one gives the most
   * conservative bound, and using it on every partition keeps them deleting the same checkpoints.
   */
  private Instant calculateWindowBound(final List<BackupStatus> anchorBackups) {
    final var earliestTimestamp =
        anchorBackups.stream()
            .map(
                backup ->
                    Objects.<Instant>requireNonNull(
                        backupTimestamp(backup), "anchor backup must have a timestamp"))
            .min(Comparator.naturalOrder())
            .orElseThrow();
    return earliestTimestamp.minusSeconds(retentionWindow.toSeconds());
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
      final BatchResult batch, final PartitionSweep sweep) {
    // -1 is the sentinel for "no new range start found in this batch"; checkpoint id 0 is a valid
    // id (partitions start counting from it), so it must not be mistaken for the sentinel.
    if (batch.earliestBackupInNewRange != -1L) {
      metrics.forPartition(sweep.partitionId).setEarliestBackupId(batch.earliestBackupInNewRange);
    }
    if (batch.deletableBackups.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    // Deduplicate by checkpoint ID — a single DELETE_BACKUP command handles all node copies
    final var uniqueCheckpointIds =
        batch.deletableBackups.stream()
            .mapToLong(BackupIdentifier::checkpointId)
            .distinct()
            .toArray();

    LOG.debug(
        "Sending {} DELETE_BACKUP commands for partition {}",
        uniqueCheckpointIds.length,
        sweep.partitionId);

    final var futures = new ArrayList<CompletableFuture<?>>(uniqueCheckpointIds.length);
    for (final var checkpointId : uniqueCheckpointIds) {
      final var request = new BackupDeleteRequest();
      request.setPartitionGroup(physicalTenantId);
      request.setPartitionId(sweep.partitionId);
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
              metrics.forPartition(sweep.partitionId).setBackupsDeleted(sweep.deleted);
            },
            actor)
        .whenCompleteAsync(
            (result, error) -> {
              if (error != null) {
                LOG.error(
                    "Failed to send DELETE_BACKUP commands for partition {}",
                    sweep.partitionId,
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

  /** The newest checkpoint completed on every partition and the window bound it defines. */
  private record SharedAnchor(long checkpointId, Instant windowBound) {}

  /** The state of one partition's sweep: the shared anchor and what was deleted. */
  private static final class PartitionSweep {
    private final int partitionId;
    private final SharedAnchor anchor;
    private int deleted;

    private PartitionSweep(final int partitionId, final SharedAnchor anchor) {
      this.partitionId = partitionId;
      this.anchor = anchor;
    }
  }

  private record BatchResult(
      List<BackupIdentifier> deletableBackups,
      long earliestBackupInNewRange,
      boolean reachedWindow) {}

  /** The state of one {@link #findSharedAnchor} run. Only accessed from the actor. */
  private final class SharedAnchorSearch {
    private final CompletableFuture<Optional<SharedAnchor>> result = new CompletableFuture<>();
    private final BackupStore store;
    private final List<Integer> partitions;
    private final List<BackupStatus> candidateBackups = new ArrayList<>();
    private int nextPartition;
    private long candidate = Long.MAX_VALUE;

    private SharedAnchorSearch(final BackupStore store, final List<Integer> partitions) {
      this.store = store;
      this.partitions = partitions;
    }

    private void nextPartition() {
      final var partitionId = partitions.get(nextPartition);
      final var before =
          candidate == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(candidate + 1);
      findLatestCompletedBackup(store, partitionId, before)
          .whenCompleteAsync(
              (latestCompleted, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                } else {
                  onAnswer(partitionId, latestCompleted);
                }
              },
              actor);
    }

    private void onAnswer(final int partitionId, final Optional<BackupStatus> latestCompleted) {
      if (latestCompleted.isEmpty()) {
        logNoSharedAnchor(partitionId);
        result.complete(Optional.empty());
        return;
      }

      final var backup = latestCompleted.get();
      if (backup.id().checkpointId() < candidate) {
        candidate = backup.id().checkpointId();
        candidateBackups.clear();
      }
      candidateBackups.add(backup);

      if (candidateBackups.size() == partitions.size()) {
        result.complete(
            Optional.of(new SharedAnchor(candidate, calculateWindowBound(candidateBackups))));
        return;
      }
      nextPartition = (nextPartition + 1) % partitions.size();
      nextPartition();
    }

    private void logNoSharedAnchor(final int partitionId) {
      if (candidate == Long.MAX_VALUE) {
        LOG.debug(
            "Skipping backup retention for physical tenant {}, partition {} has no completed backup",
            physicalTenantId,
            partitionId);
      } else {
        LOG.warn(
            "Skipping backup retention for physical tenant {}, no checkpoint is completed on every"
                + " partition: partition {} has no completed backup at or below checkpoint {}",
            physicalTenantId,
            partitionId,
            candidate);
      }
    }
  }
}
