/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.validation;

import static io.camunda.zeebe.backup.management.BackupMetadataSyncer.MAPPER;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
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
  private static final List<String> RANGE_AVAILABLE_TYPES = List.of("rdbms", "none");
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
    } catch (final IllegalArgumentException e) {
      return Either.left(new InvalidRequest(e.getMessage()));
    }

    return Either.right(resolvedRequest);
  }

  @VisibleForTesting
  static void validateParameters(final RestoreRequest request) {
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
      }
      case "elasticsearch", "opensearch" -> {
        Preconditions.test(
            hasTimeRange,
            "Time range restore (from/to) is not supported for %s.".formatted(databaseType));
        Preconditions.test(!hasBackupIds, "No backupId specified");
      }
      default ->
          throw new IllegalArgumentException("Invalid database type: " + request.databaseType());
    }
  }

  private Map<Integer, long[]> resolveBackups(final RestoreRequest request) {
    if (RANGE_AVAILABLE_TYPES.contains(request.databaseType())) {
      return resolveRdbmsBackups(request);
    }
    return Map.of();
  }

  private Map<Integer, long[]> resolveRdbmsBackups(final RestoreRequest request) {
    final RestorableBackups backups;
    try {
      backups = findBackups(request);
    } catch (final CompletionException e) {
      if (e.getCause() instanceof final RuntimeException exception) {
        throw exception;
      }
      throw e;
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
    if (exportedPositionSupplier == null) {
      return loadMetadataForAllPartitions(partitionCount)
          .thenApply(
              metadataByPartition ->
                  RestorePointResolver.resolve(metadataByPartition, instantFrom, instantTo, null))
          .join();
    }
    return exportedPositions(exportedPositionSupplier, partitionCount)
        .thenCombine(
            loadMetadataForAllPartitions(partitionCount),
            (exportedPositions, metadataByPartition) -> {
              LOG.info("Exported positions for all partitions: {}", exportedPositions);
              return RestorePointResolver.resolve(
                  metadataByPartition, instantFrom, instantTo, exportedPositions);
            })
        .join();
  }

  private CompletableFuture<Map<Integer, Long>> exportedPositions(
      final IntFunction<Long> positionSupplier, final int partitionCount) {
    return FuturesUtil.parTraverse(
            IntStream.rangeClosed(1, partitionCount).boxed().toList(),
            partition ->
                CompletableFuture.supplyAsync(
                    () -> {
                      final var position = positionSupplier.apply(partition);
                      if (position == null) {
                        throw new IllegalArgumentException(
                            "No exported position found for partition " + partition + " in RDBMS");
                      }

                      return Map.entry(partition, position);
                    }))
        .thenApply(
            s -> s.stream().collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue)));
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
                                new IllegalStateException(
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
      throw new IllegalArgumentException(
          "Invalid %s timestamp '%s': must be an ISO 8601 date-time.".formatted(field, value));
    }
  }
}
