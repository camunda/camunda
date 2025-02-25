/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;

public class BackupStoreCfg implements ConfigurationEntry {

  private BackupStoreType store = BackupStoreType.NONE;

  private S3BackupStoreConfig s3 = new S3BackupStoreConfig();
  private GcsBackupStoreConfig gcs = new GcsBackupStoreConfig();

  private AzureBackupStoreConfig azure = new AzureBackupStoreConfig();
  private FilesystemBackupStoreConfig filesystem = new FilesystemBackupStoreConfig();

  public S3BackupStoreConfig getS3() {
    return s3;
  }

  public void setS3(final S3BackupStoreConfig s3) {
    this.s3 = s3;
  }

  public GcsBackupStoreConfig getGcs() {
    return gcs;
  }

  public void setGcs(final GcsBackupStoreConfig gcs) {
    this.gcs = gcs;
  }

  public AzureBackupStoreConfig getAzure() {
    return azure;
  }

  public void setAzure(final AzureBackupStoreConfig azure) {
    this.azure = azure;
  }

  public FilesystemBackupStoreConfig getFilesystem() {
    return filesystem;
  }

  public void setFilesystem(final FilesystemBackupStoreConfig filesystem) {
    this.filesystem = filesystem;
  }

  public BackupStoreType getStore() {
    return store;
  }

  public void setStore(final BackupStoreType store) {
    this.store = store;
  }

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    s3.init(globalConfig, brokerBase);
    gcs.init(globalConfig, brokerBase);
    azure.init(globalConfig, brokerBase);
    filesystem.init(globalConfig, brokerBase);
  }

  @Override
  public String toString() {
    return switch (store) {
      case NONE -> "BackupStoreCfg{" + "store=" + store + '}';
      case S3 -> "BackupStoreCfg{" + "store=" + store + ", s3=" + s3 + '}';
      case GCS -> "BackupStoreCfg{" + "store=" + store + ", gcs=" + gcs + '}';
      case AZURE -> "BackupStoreCfg{" + "store=" + store + ", azure=" + azure + '}';
      case FILESYSTEM -> "BackupStoreCfg{" + "store=" + store + ", azure=" + azure + '}';
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
    /**
     * When type = AZURE, {@link io.camunda.zeebe.backup.azure.AzureBackupStore} will be used as the
     * backup store
     */
    AZURE,
    /**
     * When type = FILESYSTEM, {@link io.camunda.zeebe.backup.filesystem.FilesystemBackupStore} will
     * be used as the backup store
     */
    FILESYSTEM,

    /** Set type = NONE when no backup store is available. No backup will be taken. */
    NONE
  }
}
