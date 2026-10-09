/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.validation;

import static io.camunda.zeebe.backup.management.BackupMetadataSyncer.MAPPER;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.restore.RestorePointResolver;
import io.camunda.zeebe.restore.RestorePointResolver.RestorableBackups;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Preconditions;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates {@link RestoreRequest}s submitted through the cluster-configuration API while a broker
 * is in recovery mode.
 */
@NullMarked
public final class RestoreValidator
    implements ClusterConfigurationRequestValidator<RestoreRequest, RestoreResolvedRequest> {
  private static final Logger LOG = LoggerFactory.getLogger(RestoreValidator.class);
  // The request sender's timeout is 10sec, fail validation fast if backup store is
  // unavailable to release resources.
  private static final Duration BACKUP_RESOLUTION_TIMEOUT = Duration.ofSeconds(10);
  private final @Nullable BackupStore backupStore;
  private final @Nullable IntFunction<@Nullable Long> exportedPositionSupplier;
  private final int partitionCount;

  public RestoreValidator(
      final int partitionCount,
      final @Nullable BackupStore backupStore,
      final @Nullable IntFunction<@Nullable Long> exportedPositionSupplier) {
    this.backupStore = backupStore;
    this.exportedPositionSupplier = exportedPositionSupplier;
    this.partitionCount = partitionCount;
  }

  @Override
  public Class<RestoreRequest> requestType() {
    return RestoreRequest.class;
  }

  @Override
  public Either<Exception, RestoreResolvedRequest> validate(final RestoreRequest request) {
    if (backupStore == null) {
      return Either.left(
          new IllegalStateException(
              "Cannot restore: no backup store is configured on this broker."));
    }
    final RestoreResolvedRequest resolvedRequest;
    try {
      validateParameters(request);
      final var partitionBackups = resolveBackups(request);
      resolvedRequest = new RestoreResolvedRequest(partitionBackups, request.dryRun());
    } catch (final Exception e) {
      return Either.left(e);
    }

    return Either.right(resolvedRequest);
  }

  @VisibleForTesting
  void validateParameters(final RestoreRequest request) {
    final var parameters = request.arguments().parameters();
    final var instantFrom = parseTimestamp(parameters.from(), "from");
    final var instantTo = parseTimestamp(parameters.to(), "to");
    final var hasTimeRange = hasTimeRange(instantFrom, instantTo);
    final var hasBackupIds = !parameters.backupIds().isEmpty();
    Preconditions.test(
        hasBackupIds && hasTimeRange,
        "Cannot specify both backupId and from/to parameters. Choose one approach.");

    final var databaseType = request.arguments().databaseType().toLowerCase();
    switch (databaseType) {
      case "rdbms", "none" -> {
        Preconditions.test(
            hasTimeRange && !request.arguments().continuousBackups(),
            "Time range restore (from/to) is only supported for continuous backups.");

        Preconditions.test(
            instantFrom != null && instantTo != null && instantFrom.isAfter(instantTo),
            "Invalid time range: from (%s) must be before to (%s)"
                .formatted(instantFrom, instantTo));
        Preconditions.test(
            !hasBackupIds && exportedPositionSupplier == null,
            "Cannot resolve a restore point: no backupId was specified and no exported-position "
                + "data is available. Configure RDBMS as the secondary storage to enable "
                + "time-range restores, or specify a backupId.");
      }
      case "elasticsearch", "opensearch" -> {
        Preconditions.test(
            hasTimeRange,
            "Time range restore (from/to) is not supported for %s.".formatted(databaseType));
        Preconditions.test(!hasBackupIds, "No backupId specified");
        Preconditions.test(
            parameters.backupIds().size() > 1,
            "Cannot restore from multiple backups against database type %s"
                .formatted(databaseType));
      }
      default ->
          throw new IllegalStateException(
              "Invalid database type: " + request.arguments().databaseType());
    }
  }

  private Map<Integer, long[]> resolveBackups(final RestoreRequest request) {
    final var backupIds = request.arguments().parameters().backupIds();
    if (!backupIds.isEmpty()) {
      return resolveBackupsByPartition(backupIds);
    }
    return resolveRdbmsRangeBackups(request);
  }

  private Map<Integer, long[]> resolveBackupsByPartition(final List<Long> backupIds) {
    final var ids = backupIds.stream().mapToLong(Long::longValue).sorted().toArray();
    final var store =
        Objects.requireNonNull(backupStore, "Backup store must be configured to load backups");
    final var restoredPartitionCount = backupPartitionCount(store, ids[ids.length - 1]);
    if (exportedPositionSupplier != null) {
      // A database that already holds exported positions has to belong to the partitions of the
      // backup. One without any is restored from scratch, so the backup alone decides.
      final var exportedPartitionCount = availableExportedPositions(exportedPositionSupplier);
      if (!exportedPartitionCount.isEmpty()) {
        verifyPartitionCountsAgree(
            ids[ids.length - 1], restoredPartitionCount, exportedPartitionCount.size());
      }
    }
    return awaitResult(
            FuturesUtil.parTraverse(
                IntStream.rangeClosed(1, restoredPartitionCount).boxed().toList(),
                partition -> verifyBackupsExist(store, partition, ids)))
        .stream()
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private CompletableFuture<Entry<Integer, long[]>> verifyBackupsExist(
      final BackupStore store, final int partitionId, final long[] backupIds) {
    return FuturesUtil.traverseIgnoring(
            Arrays.stream(backupIds).boxed().toList(),
            backupId -> verifyBackupExists(store, partitionId, backupId),
            Runnable::run)
        .thenApply(ignored -> Map.entry(partitionId, backupIds));
  }

  private CompletableFuture<Void> verifyBackupExists(
      final BackupStore store, final int partitionId, final long backupId) {
    final var searchPattern =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(backupId));
    return store
        .list(searchPattern)
        .thenAccept(
            statuses ->
                statuses.stream()
                    .filter(status -> status.statusCode() == BackupStatusCode.COMPLETED)
                    .findAny()
                    .orElseThrow(
                        () ->
                            new NoSuchElementException(
                                "No completed backup found for partition %d with backup id %d"
                                    .formatted(partitionId, backupId))));
  }

  private Map<Integer, long[]> resolveRdbmsRangeBackups(final RestoreRequest request) {
    final var backups = findBackups(request);
    return backups.backupsByPartitionId().entrySet().stream()
        .collect(
            Collectors.toMap(
                Entry::getKey,
                e ->
                    e.getValue().stream()
                        .mapToLong(BackupMetadata.CheckpointEntry::checkpointId)
                        .toArray()));
  }

  private RestorableBackups findBackups(final RestoreRequest request) {
    final Instant instantTo = parseTimestamp(request.arguments().parameters().to(), "to");
    final Instant instantFrom = parseTimestamp(request.arguments().parameters().from(), "from");
    // Exported positions are mandatory for a range restore: validateParameters rejects the request
    // without a way to read them, and they are what the partitions to restore are taken from.
    final var exportedPositions =
        requireExportedPositions(
            exportedPositions(
                Objects.requireNonNull(exportedPositionSupplier, "Exported positions are required"),
                partitionCount));
    LOG.info("Exported positions for all partitions: {}", exportedPositions);
    final var metadataByPartition = loadMetadataForAllPartitions(exportedPositions.size());
    final var restorableBackups =
        RestorePointResolver.resolve(
            metadataByPartition, instantFrom, instantTo, exportedPositions);
    verifyLastBackupsHoldThePartitions(restorableBackups, exportedPositions.size());
    return restorableBackups;
  }

  @VisibleForTesting
  void verifyLastBackupsHoldThePartitions(
      final RestorableBackups restorableBackups, final int exportedPartitionCount) {
    final var store =
        Objects.requireNonNull(backupStore, "Backup store must be configured to load backups");
    restorableBackups.backupsByPartitionId().values().stream()
        .map(backups -> backups.getLast().checkpointId())
        .distinct()
        .sorted()
        .forEach(
            lastBackup ->
                verifyPartitionCountsAgree(
                    lastBackup, backupPartitionCount(store, lastBackup), exportedPartitionCount));
  }

  /**
   * The partitions to restore are those of the last backup, which have to be the ones the RDBMS
   * holds exported positions for.
   */
  private static void verifyPartitionCountsAgree(
      final long backupId, final int backupPartitionCount, final int exportedPartitionCount) {
    if (backupPartitionCount != exportedPartitionCount) {
      throw new IllegalStateException(
          ("Cannot restore: backup %d was taken with %d partitions, but the RDBMS holds exported "
                  + "positions for %d")
              .formatted(backupId, backupPartitionCount, exportedPartitionCount));
    }
  }

  /**
   * The partition count recorded in the descriptor of the given backup, on any partition since
   * every partition's backup records the same count, rejected if the group holds fewer partitions.
   *
   * <p>Restoring a backup taken before a scale up leaves the group with that backup's partitions,
   * so restoring forward again to a backup with more partitions than that is rejected here: the
   * group has no partitions to restore them into.
   */
  private int backupPartitionCount(final BackupStore store, final long backupId) {
    final var backupPartitionCount =
        awaitResult(store.list(lookupWildcard(backupId))).stream()
            .filter(status -> status.statusCode() == BackupStatusCode.COMPLETED)
            .findAny()
            .flatMap(BackupStatus::descriptor)
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "No completed backup found with backup id %d".formatted(backupId)))
            .numberOfPartitions();
    if (backupPartitionCount > partitionCount) {
      throw new IllegalArgumentException(
          "Cannot restore backup %d: it has %d partitions, but the partition group only has %d"
              .formatted(backupId, backupPartitionCount, partitionCount));
    }
    return backupPartitionCount;
  }

  /**
   * The exported positions of the partitions the RDBMS holds, which are the partitions of the
   * backup to restore: partitions 1 up to the first one without an exported position. Partitions
   * without one after that are fine, e.g. those a scale-up added after the database was restored to
   * the backups, but a position after a missing one means the database is inconsistent.
   */
  private Map<Integer, Long> exportedPositions(
      final IntFunction<@Nullable Long> positionSupplier, final int partitionCount) {
    final var positions = new HashMap<Integer, Long>();
    var firstMissing = 0;
    for (int partition = 1; partition <= partitionCount; partition++) {
      final var position = positionSupplier.apply(partition);
      if (position == null) {
        if (firstMissing == 0) {
          firstMissing = partition;
        }
      } else if (firstMissing != 0) {
        throw new IllegalStateException(
            "The RDBMS holds an exported position for partition %d but none for partition %d"
                .formatted(partition, firstMissing));
      } else {
        positions.put(partition, position);
      }
    }
    return Map.copyOf(positions);
  }

  /**
   * The exported positions of the RDBMS, or none if they cannot be read. A database that is about
   * to be restored into may not have its schema yet, which only the restore creates, so a failure
   * to read is not an inconsistency; a gap between the positions still is.
   */
  private Map<Integer, Long> availableExportedPositions(
      final IntFunction<@Nullable Long> positionSupplier) {
    try {
      return exportedPositions(positionSupplier, partitionCount);
    } catch (final IllegalStateException inconsistentPositions) {
      throw inconsistentPositions;
    } catch (final RuntimeException e) {
      LOG.warn("Exported positions are not available, restoring from the backup alone", e);
      return Map.of();
    }
  }

  private static Map<Integer, Long> requireExportedPositions(final Map<Integer, Long> positions) {
    if (positions.isEmpty()) {
      throw new IllegalStateException("No exported position found for partition 1 in RDBMS");
    }
    return positions;
  }

  private List<BackupMetadata> loadMetadataForAllPartitions(final int partitionCount) {
    final var store =
        Objects.requireNonNull(backupStore, "Backup store must be configured to load backups");
    final var metadataByPartition =
        awaitResult(
            FuturesUtil.parTraverse(
                IntStream.rangeClosed(1, partitionCount).boxed().toList(),
                partition ->
                    store
                        .loadBackupMetadata(partition)
                        .thenApply(
                            optBytes -> optBytes.flatMap(bytes -> parseMetadata(partition, bytes)))
                        .thenApply(metadata -> Map.entry(partition, metadata))));

    final var missingPartitions =
        metadataByPartition.stream()
            .filter(entry -> entry.getValue().isEmpty())
            .map(Entry::getKey)
            .toList();
    if (!missingPartitions.isEmpty()) {
      throw new IllegalStateException(
          "No backup metadata found for partition(s): " + missingPartitions);
    }
    return metadataByPartition.stream().map(entry -> entry.getValue().orElseThrow()).toList();
  }

  private static Optional<BackupMetadata> parseMetadata(final int partitionId, final byte[] bytes) {
    try {
      return Optional.of(MAPPER.readValue(bytes, BackupMetadata.class));
    } catch (final IOException e) {
      LOG.warn("Failed to parse backup metadata for partition {}", partitionId, e);
      return Optional.empty();
    }
  }

  private BackupIdentifierWildcardImpl lookupWildcard(final long backupId) {
    return new BackupIdentifierWildcardImpl(
        Optional.empty(), Optional.empty(), CheckpointPattern.of(backupId));
  }

  private static <T> T awaitResult(final CompletableFuture<T> future) {
    try {
      return future.get(BACKUP_RESOLUTION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof final RuntimeException cause) {
        throw cause;
      }
      throw new IllegalStateException("Failed to resolve backups", e.getCause());
    } catch (final TimeoutException e) {
      throw new IllegalStateException(
          "Timed out after %s waiting to resolve backups".formatted(BACKUP_RESOLUTION_TIMEOUT), e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while resolving backups", e);
    }
  }

  private static boolean hasTimeRange(@Nullable final Instant from, @Nullable final Instant to) {
    return from != null || to != null;
  }

  private static @Nullable Instant parseTimestamp(
      final @Nullable String value, final String field) {
    if (value == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (final DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Invalid %s timestamp '%s': must be an ISO 8601 date-time.".formatted(field, value));
    }
  }
}
