/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;

public class BackupCfg implements ConfigurationEntry {

  private BackupStoreType store = BackupStoreType.NONE;

  private S3BackupStoreConfig s3 = new S3BackupStoreConfig();
  private GcsBackupStoreConfig gcs = new GcsBackupStoreConfig();
  private AzureBackupStoreConfig azure = new AzureBackupStoreConfig();
  private FilesystemBackupStoreConfig filesystem = new FilesystemBackupStoreConfig();

  private BackupSchedulerRetentionCfg retention = new BackupSchedulerRetentionCfg();
  private boolean continuous = false;
  private boolean required = false;
  private String schedule;
  private Duration checkpointInterval;
  private long offset = 0L;

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
    final StringBuilder sb = new StringBuilder("BackupStoreCfg{");
    sb.append("store=").append(store);
    switch (store) {
      case S3 -> sb.append(", s3=").append(s3);
      case GCS -> sb.append(", gcs=").append(gcs);
      case AZURE -> sb.append(", azure=").append(azure);
      case FILESYSTEM -> sb.append(", filesystem=").append(filesystem);
      default -> {}
    }
    sb.append(", continuous=")
        .append(continuous)
        .append(", required=")
        .append(required)
        .append(", schedule=")
        .append(schedule)
        .append(", checkpointInterval=")
        .append(checkpointInterval)
        .append(", offset=")
        .append(offset)
        .append(", retention=")
        .append(retention)
        .append('}');
    return sb.toString();
  }

  public boolean isContinuous() {
    return continuous;
  }

  public void setContinuous(final boolean continuous) {
    this.continuous = continuous;
  }

  public Schedule getSchedule() {
    return Schedule.parseSchedule(schedule);
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

  public boolean isRequired() {
    return required;
  }

  public void setRequired(final boolean required) {
    this.required = required;
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
    NONE
  }
}
