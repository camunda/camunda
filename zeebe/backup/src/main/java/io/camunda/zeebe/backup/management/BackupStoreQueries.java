/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.ListOptions;
import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only queries against a {@link BackupStore} for a given partition. Shared by the leader-side
 * backup manager and the recovery-mode read-only manager so the store listing logic is implemented
 * once. All operations are pure store lookups and never mutate backup state.
 */
public final class BackupStoreQueries {

  private static final Logger LOG = LoggerFactory.getLogger(BackupStoreQueries.class);

  private final BackupStore backupStore;
  private final Map<Listing, CompletableFuture<List<BackupStatus>>> inFlightListings =
      new ConcurrentHashMap<>();

  public BackupStoreQueries(final BackupStore backupStore) {
    this.backupStore = backupStore;
  }

  /**
   * Returns the highest-ranked status of the backup with the given checkpoint id, or {@link
   * Optional#empty()} if no matching backup exists in the store. Callers decide how to represent a
   * missing backup (e.g. a {@link BackupStatusCode#DOES_NOT_EXIST} placeholder).
   */
  public ActorFuture<Optional<BackupStatus>> getBackupStatus(
      final int partitionId, final long checkpointId, final ConcurrencyControl executor) {
    final ActorFuture<Optional<BackupStatus>> result = executor.createFuture();
    final var wildcard = wildcard(partitionId, CheckpointPattern.of(checkpointId));
    LOG.atDebug().addKeyValue("pattern", wildcard).setMessage("Querying backup status").log();
    executor.run(
        () ->
            backupStore
                .list(wildcard)
                .whenCompleteAsync(
                    (statuses, error) -> {
                      if (error != null) {
                        LOG.atError()
                            .addKeyValue("pattern", wildcard)
                            .setCause(error)
                            .setMessage("Failed to query backup status")
                            .log();
                        result.completeExceptionally(error);
                      } else {
                        LOG.atTrace()
                            .addKeyValue("pattern", wildcard)
                            .addKeyValue("found", statuses.size())
                            .setMessage("Queried backup status")
                            .log();
                        result.complete(statuses.stream().max(BackupStatusCode.BY_STATUS));
                      }
                    },
                    executor));
    return result;
  }

  /**
   * Lists the page of backups of the given partition matching the given pattern, ordered by
   * checkpoint id as the options request. The page is capped at {@link BackupApi#MAX_PAGE_SIZE}.
   *
   * <p>Identical listings that arrive while one is still running share its result. A client that
   * times out and retries a slow listing therefore does not start another scan of the store on top
   * of the one still running.
   */
  public ActorFuture<Collection<BackupStatus>> listBackups(
      final int partitionId,
      final String pattern,
      final ListOptions options,
      final ConcurrencyControl executor) {
    final ActorFuture<Collection<BackupStatus>> result = executor.createFuture();
    final var listing =
        new Listing(wildcard(partitionId, CheckpointPattern.of(pattern)), cap(options));
    executor.run(
        () ->
            listOnce(listing)
                .whenCompleteAsync(
                    (statuses, error) -> {
                      if (error != null) {
                        result.completeExceptionally(error);
                      } else {
                        result.complete(statuses);
                      }
                    },
                    executor));
    return result;
  }

  private CompletableFuture<List<BackupStatus>> listOnce(final Listing listing) {
    final var inFlight = inFlightListings.get(listing);
    if (inFlight != null) {
      LOG.atDebug().addKeyValue("listing", listing).setMessage("Joining in-flight listing").log();
      return inFlight;
    }
    final var result = new CompletableFuture<List<BackupStatus>>();
    inFlightListings.put(listing, result);
    backupStore
        .list(listing.wildcard(), listing.options())
        .whenComplete(
            (statuses, error) -> {
              inFlightListings.remove(listing, result);
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                result.complete(statuses);
              }
            });
    return result;
  }

  /** Caps a requested page at the maximum. An unbounded listing stays unbounded. */
  private static ListOptions cap(final ListOptions options) {
    if (options.limit().isEmpty() || options.limit().getAsInt() <= BackupApi.MAX_PAGE_SIZE) {
      return options;
    }
    return new ListOptions(
        options.order(), options.startExclusive(), OptionalInt.of(BackupApi.MAX_PAGE_SIZE));
  }

  private static BackupIdentifierWildcardImpl wildcard(
      final int partitionId, final CheckpointPattern pattern) {
    return new BackupIdentifierWildcardImpl(Optional.empty(), Optional.of(partitionId), pattern);
  }

  private record Listing(BackupIdentifierWildcardImpl wildcard, ListOptions options) {}
}
