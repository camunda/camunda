/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.backup.filesystem.FilesystemLogArchiveStore;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.backup.s3.S3LogArchiveStore;
import org.jspecify.annotations.NullMarked;

/** Creates the log archive store from the store settings of the existing backup config. */
@NullMarked
public final class LogArchiveStoreFactory {

  private LogArchiveStoreFactory() {}

  /**
   * @throws IllegalArgumentException if the configured store type has no log archive store
   */
  public static LogArchiveStore createStore(final BackupCfg backupCfg) {
    return switch (backupCfg.getStore()) {
      case S3 ->
          S3LogArchiveStore.of(
              S3BackupStoreConfig.toStoreConfig(
                  backupCfg.getS3(), backupCfg.getReadTimeout(), backupCfg.getWriteTimeout()));
      case FILESYSTEM ->
          FilesystemLogArchiveStore.of(
              FilesystemBackupStoreConfig.toStoreConfig(backupCfg.getFilesystem()));
      default ->
          throw new IllegalArgumentException(
              "Expected backup store type S3 or FILESYSTEM for the log archive, but got "
                  + backupCfg.getStore());
    };
  }
}
