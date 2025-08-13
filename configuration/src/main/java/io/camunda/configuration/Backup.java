/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class Backup {
  private static final String PREFIX = "camunda.data.backup";

  private static final Map<String, String> LEGACY_OPERATE_BACKUP_PROPERTIES =
      Map.of(
          "repositoryName",
          "camunda.operate.backup.repositoryName",
          "snapshotTimeout",
          "camunda.operate.backup.snapshotTimeout",
          "incompleteCheckTimeoutInSeconds",
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds");

  private static final Map<String, String> LEGACY_TASKLIST_BACKUP_PROPERTIES =
      Map.of("repositoryName", "camunda.tasklist.backup.repositoryName");

  private static final Map<String, String> LEGACY_BROKER_BACKUP_PROPERTIES =
      Map.of("store", "zeebe.broker.data.backup.store");

  private Map<String, String> legacyPropertyMap = LEGACY_OPERATE_BACKUP_PROPERTIES;

  /** Set the ES / OS snapshot repository name */
  private String repositoryName;

  /** TBD. Not available */
  private int snapshotTimeout = 0;

  /** TBD. Not available */
  private Duration incompleteCheckTimeout = Duration.ofMinutes(5);

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
   *   <li>Use FILESYSTEM to use filesystem storage
   * </ul>
   */
  private BackupStoreType store = BackupStoreType.NONE;

  /** Configuration for backup store AWS S3 */
  private S3 s3 = new S3();

  /** Configuration for backup store GCS */
  private Gcs gcs = new Gcs();

  /** Configuration for backup store Filesystem */
  private Filesystem filesystem = new Filesystem();

  /** Configuration for backup store Azure */
  private Azure azure = new Azure();

  public String getRepositoryName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".repository-name",
        repositoryName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertyMap.get("repositoryName")));
  }

  public void setRepositoryName(final String repositoryName) {
    this.repositoryName = repositoryName;
  }

  public int getSnapshotTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".snapshot-timeout",
        snapshotTimeout,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertyMap.get("snapshotTimeout")));
  }

  public void setSnapshotTimeout(final int snapshotTimeout) {
    this.snapshotTimeout = snapshotTimeout;
  }

  public Duration getIncompleteCheckTimeout() {
    final long incompleteCheckTimeoutInSeconds =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            PREFIX + ".incomplete-check-timeout",
            incompleteCheckTimeout.getSeconds(),
            Long.class,
            BackwardsCompatibilityMode.SUPPORTED,
            Set.of(legacyPropertyMap.get("incompleteCheckTimeoutInSeconds")));

    return Duration.ofSeconds(incompleteCheckTimeoutInSeconds);
  }

  public void setIncompleteCheckTimeout(final Duration incompleteCheckTimeout) {
    this.incompleteCheckTimeout = incompleteCheckTimeout;
  }

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

  public Filesystem getFilesystem() {
    return filesystem;
  }

  public void setFilesystem(final Filesystem filesystem) {
    this.filesystem = filesystem;
  }

  public Azure getAzure() {
    return azure;
  }

  public void setAzure(final Azure azure) {
    this.azure = azure;
  }

  public BackupStoreType getStore() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".store",
        store,
        BackupStoreType.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertyMap.get("store")));
  }

  public void setStore(final BackupStoreType store) {
    this.store = store;
  }

  @Override
  public Backup clone() {
    final Backup copy = new Backup();
    copy.repositoryName = repositoryName;
    copy.snapshotTimeout = snapshotTimeout;
    copy.incompleteCheckTimeout = incompleteCheckTimeout;
    copy.store = store;
    copy.s3 = s3;
    copy.gcs = gcs;
    copy.filesystem = filesystem;
    copy.azure = azure;

    return copy;
  }

  public Backup withOperateBackupProperties() {
    final Backup copy = clone();
    copy.legacyPropertyMap = LEGACY_OPERATE_BACKUP_PROPERTIES;
    return copy;
  }

  public Backup withTasklistBackupProperties() {
    final Backup copy = clone();
    copy.legacyPropertyMap = LEGACY_TASKLIST_BACKUP_PROPERTIES;
    return copy;
  }

  public Backup withBrokerBackupProperties() {
    final Backup copy = clone();
    copy.legacyPropertyMap = LEGACY_BROKER_BACKUP_PROPERTIES;
    return copy;
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
