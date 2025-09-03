/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.event.Level;

public class LoggingBackupStore implements BackupStore {

  private final BackupStore store;
  private final Logger logger;
  private final Level level;

  public LoggingBackupStore(final BackupStore store, final Logger logger, final Level level) {
    this.store = store;
    this.logger = logger;
    this.level = level;
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    logger.atLevel(level).log("Saving {}", backup.id());
    return store.save(backup);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    logger.atLevel(level).log("Querying status of {}", id);
    return store.getStatus(id);
  }

  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    logger.atLevel(level).log("Querying status with wildcard {}", wildcard);
    return store.list(wildcard);
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    logger.atLevel(level).log("Deleting {}", id);
    return store.delete(id);
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    logger.atLevel(level).log("Restoring {} to {}", id, targetFolder);
    return store.restore(id, targetFolder);
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    logger.atLevel(level).log("Marking {} as failed: {}", id, failureReason);
    return store.markFailed(id, failureReason);
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    logger.atLevel(level).log("Closing backup store");
    return store.closeAsync();
  }
}
