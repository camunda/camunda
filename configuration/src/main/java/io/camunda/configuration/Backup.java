/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class Backup {
  private static final String PREFIX = "camunda.data.backup.";
  private static final Set<String> LEGACY_STORE_PROPERTIES =
      Set.of("zeebe.broker.data.backup.store");

  /**
   * Set the backup store type. Supported values are [NONE, S3, GCS, AZURE, FILESYSTEM]. Default
   * value is NONE
   *
   * <ul>
   *   <li>When NONE, no backup store is configured and no backup will be taken.
   *   <li>Use S3 to use any S3 compatible storage
   *       (https://docs.aws.amazon.com/AmazonS3/latest/API/Type_API_Reference.html).
   *   <li>Use GCS to use Google Cloud Storage (https://cloud.google.com/storage/)
   *   <li>Use AZURE to use Azure Storage
   *       (https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blobs-introduction)
   *   <li>Use FILESYSTEM tbd
   * </ul>
   */
  private BackupStoreType store = BackupStoreType.NONE;

  /** Configuration for backup store AWS S3 */
  private S3 s3 = new S3();

  /** Configuration for backup store GCS */
  private Gcs gcs = new Gcs();

  /** Configuration for backup store Azure */
  private Azure azure = new Azure();

  public S3 getS3() {
    return s3;
  }

  public void setS3(final S3 s3) {
    this.s3 = s3;
  }

  public Gcs getGcs() {
    return gcs;
  }

  public void setGcs(final Gcs gcs) {
    this.gcs = gcs;
  }

  public Azure getAzure() {
    return azure;
  }

  public void setAzure(final Azure azure) {
    this.azure = azure;
  }

  public BackupStoreType getStore() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "store",
        store,
        BackupStoreType.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_STORE_PROPERTIES);
  }

  public void setStore(final BackupStoreType store) {
    this.store = store;
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
