/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import java.util.concurrent.CompletableFuture;

// A placeholder backup store until a proper backup store is available
final class NoopBackupStore implements BackupStore {
  public static final NoopBackupStore INSTANCE = new NoopBackupStore();

  private NoopBackupStore() {}

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("No backup store configured"));
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("No backup store configured"));
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("No backup store configured"));
  }

  @Override
  public CompletableFuture<Void> markFailed(final BackupIdentifier id) {
    return CompletableFuture.completedFuture(null);
  }
}
