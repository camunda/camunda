/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadataCodec;
import io.camunda.zeebe.backup.common.BackupMetadataManifest;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Reads per-partition backup metadata manifests from the backup store. Delegates to {@link
 * BackupMetadataCodec} for loading and deserialization.
 */
@NullMarked
public final class BackupMetadataReader {

  private final BackupStore backupStore;

  public BackupMetadataReader(final BackupStore backupStore) {
    this.backupStore = backupStore;
  }

  /**
   * Loads the backup metadata manifest for the given partition.
   *
   * @param partitionId the partition to load metadata for
   * @return the manifest, or empty if no valid metadata exists
   */
  public CompletableFuture<Optional<BackupMetadataManifest>> load(final int partitionId) {
    return BackupMetadataCodec.load(backupStore, partitionId);
  }
}
