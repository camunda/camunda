/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.ConfigurationException.BucketDoesNotExistException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public final class GcsBackupStore implements BackupStore {
  private final GcsBackupConfig config;

  public GcsBackupStore(final GcsBackupConfig config) {
    this.config = config;
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    throw new UnsupportedOperationException();
  }

  public static Storage buildClient(GcsBackupConfig config) {
    return StorageOptions.newBuilder()
        .setHost(config.connection().host())
        .setCredentials(config.connection().auth().credentials())
        .build()
        .getService();
  }

  public static void validateConfig(GcsBackupConfig config) throws Exception {
    try (final var storage = buildClient(config)) {
      final var bucket = storage.get(config.bucketName());
      if (bucket == null) {
        throw new BucketDoesNotExistException(config.bucketName());
      }
    }
  }
}
