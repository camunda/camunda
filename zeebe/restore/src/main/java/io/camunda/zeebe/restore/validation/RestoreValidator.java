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
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidState;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.restore.RestorePointResolver;
import io.camunda.zeebe.restore.RestorePointResolver.RestorableBackups;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Preconditions;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
  private final @Nullable BackupStore backupStore;
  private final @Nullable IntFunction<Long> exportedPositionSupplier;
  private final int partitionCount;

  public RestoreValidator(
      final int partitionCount,
      final @Nullable BackupStore backupStore,
      final @Nullable IntFunction<Long> exportedPositionSupplier) {
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
          new InvalidRequest("Cannot restore: no backup store is configured on this broker."));
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
    final var instantFrom = parseTimestamp(request.from(), "from");
    final var instantTo = parseTimestamp(request.to(), "to");
    final var hasTimeRange = hasTimeRange(instantFrom, instantTo);
    final var hasBackupIds = !request.backupIds().isEmpty();
    Preconditions.test(
        hasBackupIds && hasTimeRange,
        "Cannot specify both backupId and from/to parameters. Choose one approach.");

    final var databaseType = request.databaseType().toLowerCase();
    switch (databaseType) {
      case "rdbms", "none" -> {
        Preconditions.test(
            hasTimeRange && !request.continuousBackups(),
            "Time range restore (from/to) is only supported for continuous backups.");

        Preconditions.test(
            instantFrom != null && instantTo != null && instantFrom.isAfter(instantTo),
            "Invalid time range: from (%s) must be before to (%s)"
                .formatted(instantFrom, instantTo));
        Preconditions.test(
            !hasBackupIds && exportedPositionSupplier == null,
            "Expected either backupIds or exportedPositionSupplier to be registered");
      }
      case "elasticsearch", "opensearch" -> {
        Preconditions.test(
            hasTimeRange,
            "Time range restore (from/to) is not supported for %s.".formatted(databaseType));
        Preconditions.test(!hasBackupIds, "No backupId specified");
      }
      default -> throw new InvalidState("Invalid database type: " + request.databaseType());
    }
  }

  private Map<Integer, long[]> resolveBackups(final RestoreRequest request) {
    if (!request.backupIds().isEmpty()) {
      return resolveBackupsByPartition(request.backupIds());
    }
    return resolveRdbmsRangeBackups(request);
  }

  private Map<Integer, long[]> resolveBackupsByPartition(final List<Long> backupIds) {
    final var ids = backupIds.stream().mapToLong(Long::longValue).sorted().toArray();
    final var store =
        Objects.requireNonNull(backupStore, "Backup store must be configured to load backups");
    try {
      return FuturesUtil.parTraverse(
              IntStream.rangeClosed(1, partitionCount).boxed().toList(),
              partition -> verifyBackupsExist(store, partition, ids))
          .join()
          .stream()
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    } catch (final CompletionException e) {
      if (e.getCause() instanceof final RuntimeException exception) {
        throw exception;
      }
      throw e;
    }
  }

  private CompletableFuture<Entry<Integer, long[]>> verifyBackupsExist(
      final BackupStore store, final int partitionId, final long[] backupIds) {
    var chain = CompletableFuture.completedFuture((Void) null);
    for (final long backupId : backupIds) {
      chain = chain.thenCompose(ignored -> verifyBackupExists(store, partitionId, backupId));
    }
    return chain.thenApply(ignored -> Map.entry(partitionId, backupIds));
  }

  private CompletableFuture<Void> verifyBackupExists(
      final BackupStore store, final int partitionId, final long backupId) {
    final var searchPattern =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(backupId));
    return store
        .list(searchPattern)
        .thenAccept(
            statuses -> {
              final var exists =
                  statuses.stream()
                      .anyMatch(status -> status.statusCode() == BackupStatusCode.COMPLETED);
              if (!exists) {
                throw new NotFound(
                    "No completed backup found for partition %d with backup id %d"
                        .formatted(partitionId, backupId));
              }
            });
  }

  private Map<Integer, long[]> resolveRdbmsRangeBackups(final RestoreRequest request) {
    final RestorableBackups backups;
    try {
      backups = findBackups(request);
    } catch (final CompletionException e) {
      if (e.getCause() instanceof final IllegalStateException cause) {
        throw new InvalidState(cause.getMessage(), cause);
      }
      if (e.getCause() instanceof final RuntimeException exception) {
        throw exception;
      }
      throw e;
    } catch (final IllegalStateException e) {
      throw new InvalidState(e.getMessage(), e);
    }

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
    final Instant instantTo = parseTimestamp(request.to(), "to");
    final Instant instantFrom = parseTimestamp(request.from(), "from");
    final var exportedPositions =
        exportedPositionSupplier == null
            ? null
            : exportedPositions(exportedPositionSupplier, partitionCount);
    LOG.info("Exported positions for all partitions: {}", exportedPositions);
    final var metadataByPartition = loadMetadataForAllPartitions(partitionCount).join();
    return RestorePointResolver.resolve(
        metadataByPartition, instantFrom, instantTo, exportedPositions);
  }

  private Map<Integer, Long> exportedPositions(
      final IntFunction<Long> positionSupplier, final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .boxed()
        .collect(
            Collectors.toUnmodifiableMap(
                partition -> partition,
                partition -> {
                  final var position = positionSupplier.apply(partition);
                  if (position == null) {
                    throw new InvalidState(
                        "No exported position found for partition " + partition + " in RDBMS");
                  }
                  return position;
                }));
  }

  private CompletableFuture<List<BackupMetadata>> loadMetadataForAllPartitions(
      final int partitionCount) {
    final var store =
        Objects.requireNonNull(backupStore, "Backup store must be configured to load backups");
    return FuturesUtil.parTraverse(
        IntStream.rangeClosed(1, partitionCount).boxed().toList(),
        partition ->
            store
                .loadBackupMetadata(partition)
                .thenApply(
                    optBytes ->
                        optBytes.flatMap(
                            bytes -> {
                              try {
                                return Optional.of(MAPPER.readValue(bytes, BackupMetadata.class));
                              } catch (final IOException e) {
                                LOG.warn("Failed to parse backup metadata", e);
                                return Optional.empty();
                              }
                            }))
                .thenApply(
                    opt ->
                        opt.orElseThrow(
                            () ->
                                new InvalidState(
                                    "No backup metadata found for partition " + partition))));
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
      throw new InvalidRequest(
          "Invalid %s timestamp '%s': must be an ISO 8601 date-time.".formatted(field, value));
    }
  }
}
