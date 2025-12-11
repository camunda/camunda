/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.broker.system.configuration.backup.BackupSchedulerRetentionCfg;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class PrimaryStorageBackup implements Cloneable {
  private static final String PREFIX = "camunda.data.primary-storage.backup";

  private static final Set<String> LEGACY_BROKER_STORE =
      Set.of("zeebe.broker.data.backup.store", "camunda.data.backup.store");

  private static final Set<String> LEGACY_CONTINUOUS_BACKUPS_PROPERTIES =
      Set.of("zeebe.broker.experimental.continuousBackups");

  /**
   * Set the backup store type. Supported values are [NONE, S3, GCS, AZURE, FILESYSTEM]. Default
   * value is NONE.
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
   *
   * <p>Note: This configuration applies to the backup of primary storage.
   */
  private BackupStoreType store = BackupStoreType.NONE;

  private boolean required = false;
  private boolean continuous = false;
  private String schedule;
  private Duration checkpointInterval;
  private long offset;

  /** Configuration for backup store AWS S3 */
  @NestedConfigurationProperty private S3 s3 = new S3();

  /** Configuration for backup store GCS */
  @NestedConfigurationProperty private Gcs gcs = new Gcs();

  /** Configuration for backup store Filesystem */
  @NestedConfigurationProperty private Filesystem filesystem = new Filesystem();

  /** Configuration for backup store Azure */
  @NestedConfigurationProperty private Azure azure = new Azure();

  @NestedConfigurationProperty
  private BackupSchedulerRetentionCfg retention = new BackupSchedulerRetentionCfg();

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
        LEGACY_BROKER_STORE);
  }

  public void setStore(final BackupStoreType store) {
    this.store = store;
  }

  public boolean isContinuous() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".continuous",
        continuous,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_CONTINUOUS_BACKUPS_PROPERTIES);
  }

  public void setContinuous(final boolean continuous) {
    this.continuous = continuous;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(final boolean required) {
    this.required = required;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(final String schedule) {
    this.schedule = schedule;
  }

  public Duration getCheckpointInterval() {
    return checkpointInterval;
  }

  public void setCheckpointInterval(final Duration checkpointInterval) {
    this.checkpointInterval = checkpointInterval;
  }

  public long getOffset() {
    return offset;
  }

  public void setOffset(final long offset) {
    this.offset = offset;
  }

  @Override
  public PrimaryStorageBackup clone() {
    try {
      final PrimaryStorageBackup clone = (PrimaryStorageBackup) super.clone();
      // TODO: copy mutable state here, so the clone can't change the internals of the original
      return clone;
    } catch (final CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  public BackupSchedulerRetentionCfg getRetention() {
    return retention;
  }

  public void setRetention(final BackupSchedulerRetentionCfg retention) {
    this.retention = retention;
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
    NONE;
  }
}
