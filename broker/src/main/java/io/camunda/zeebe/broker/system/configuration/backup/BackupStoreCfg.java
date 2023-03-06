/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;

public class BackupStoreCfg implements ConfigurationEntry {

  private BackupStoreType store = BackupStoreType.NONE;

  private S3BackupStoreConfig s3 = new S3BackupStoreConfig();
  private GCSBackupStoreConfig gcs = new GCSBackupStoreConfig();

  public S3BackupStoreConfig getS3() {
    return s3;
  }

  public void setS3(final S3BackupStoreConfig s3) {
    this.s3 = s3;
  }

  public GCSBackupStoreConfig getGcs() {
    return gcs;
  }

  public void setGcs(final GCSBackupStoreConfig gcs) {
    this.gcs = gcs;
  }

  public BackupStoreType getStore() {
    return store;
  }

  public void setStore(final BackupStoreType store) {
    this.store = store;
  }

  @Override
  public String toString() {
    return switch (store) {
      case NONE -> "BackupStoreCfg{" + "store=" + store + '}';
      case S3 -> "BackupStoreCfg{" + "store=" + store + ", s3=" + s3 + '}';
      case GCS -> "BackupStoreCfg{" + "store=" + store + ", gcs=" + gcs + '}';
    };
  }

  public enum BackupStoreType {
    /**
     * When type = S3, {@link io.camunda.zeebe.backup.s3.S3BackupStore} will be used as the backup
     * store
     */
    S3,

    /**
     * When type = GCS, {@link io.camunda.zeebe.backup.gcs.GcsBackupStore} will be used as the
     * backup store
     */
    GCS,

    /** Set type = NONE when no backup store is available. No backup will be taken. */
    NONE
  }
}
