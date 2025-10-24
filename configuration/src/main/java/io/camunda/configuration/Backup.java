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
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Backup implements Cloneable {
  private static final String PREFIX = "camunda.data.backup";

  private static final String LEGACY_OPERATE_SNAPSHOT_TIMEOUT =
      "camunda.operate.backup.snapshotTimeout";
  private static final String LEGACY_OPERATE_INCOMPLETE_CHECK_TIMEOUT_IN_SECONDS =
      "camunda.operate.backup.incompleteCheckTimeoutInSeconds";
  private static final Set<String> LEGACY_REPOSITORY_NAME_PROPERTIES =
      Set.of("camunda.tasklist.backup.repositoryName", "camunda.operate.backup.repositoryName");
  private static final String LEGACY_BROKER_STORE = "zeebe.broker.data.backup.store";

  /**
   * Set the ES / OS snapshot repository name.
   *
   * <p>Note: This setting applies to backups of secondary storage.
   */
  private String repositoryName;

  /**
   * A backup of history data consists of multiple Elasticsearch/Opensearch snapshots.
   * snapshotTimeout controls the maximum time to wait for a snapshot operation to complete during
   * backup creation. When set to 0, the system will wait indefinitely for snapshots to finish.
   *
   * <p>Note: This setting applies to backups of secondary storage.
   */
  private int snapshotTimeout = 0;

  /**
   * Defines the timeout period for determining whether an incomplete backup should be considered as
   * failed or still in progress. This property helps distinguish between backups that are actively
   * running versus those that may have stalled or failed silently.
   *
   * <p>Note: This setting applies to backups of secondary storage.
   */
  private Duration incompleteCheckTimeout = Duration.ofMinutes(5);

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

  /** Configuration for backup store AWS S3 */
  @NestedConfigurationProperty private S3 s3 = new S3();

  /** Configuration for backup store GCS */
  @NestedConfigurationProperty private Gcs gcs = new Gcs();

  /** Configuration for backup store Filesystem */
  @NestedConfigurationProperty private Filesystem filesystem = new Filesystem();

  /** Configuration for backup store Azure */
  @NestedConfigurationProperty private Azure azure = new Azure();

  public String getRepositoryName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".repository-name",
        repositoryName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_REPOSITORY_NAME_PROPERTIES);
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
        Set.of(LEGACY_OPERATE_SNAPSHOT_TIMEOUT));
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
            Set.of(LEGACY_OPERATE_INCOMPLETE_CHECK_TIMEOUT_IN_SECONDS));

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
        Set.of(LEGACY_BROKER_STORE));
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
