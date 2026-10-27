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
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Collection;
import java.util.Optional;
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

  /** Lists all backups of the given partition matching the given pattern. */
  public ActorFuture<Collection<BackupStatus>> listBackups(
      final int partitionId, final String pattern, final ConcurrencyControl executor) {
    final ActorFuture<Collection<BackupStatus>> result = executor.createFuture();
    executor.run(
        () ->
            backupStore
                .list(wildcard(partitionId, CheckpointPattern.of(pattern)))
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

  private static BackupIdentifierWildcardImpl wildcard(
      final int partitionId, final CheckpointPattern pattern) {
    return new BackupIdentifierWildcardImpl(Optional.empty(), Optional.of(partitionId), pattern);
  }
}
