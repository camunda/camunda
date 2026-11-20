/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.common.BackupReadRateLimiter;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.util.unit.DataSize;

@NullMarked
public class BackupCfg implements ConfigurationEntry {

  /** The lowest {@link #getMaxReadRate() maxReadRate} in bytes per second. */
  public static final long MIN_MAX_READ_RATE_BYTES = BackupReadRateLimiter.MIN_BYTES_PER_SECOND;

  private BackupStoreType store = BackupStoreType.NONE;

  private S3BackupStoreConfig s3 = new S3BackupStoreConfig();
  private GcsBackupStoreConfig gcs = new GcsBackupStoreConfig();
  private AzureBackupStoreConfig azure = new AzureBackupStoreConfig();
  private FilesystemBackupStoreConfig filesystem = new FilesystemBackupStoreConfig();

  private BackupSchedulerRetentionCfg retention = new BackupSchedulerRetentionCfg();
  private boolean continuous = false;
  private boolean required = false;
  private @Nullable String schedule;
  private @Nullable Duration checkpointInterval;
  private long offset = 0L;
  private @Nullable Duration readTimeout;
  private @Nullable Duration writeTimeout;
  private @Nullable DataSize maxReadRate;

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
        .append(", readTimeout=")
        .append(readTimeout)
        .append(", writeTimeout=")
        .append(writeTimeout)
        .append(", maxReadRate=")
        .append(maxReadRate)
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

  public @Nullable Duration getCheckpointInterval() {
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

  public @Nullable Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(final @Nullable Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  public @Nullable Duration getWriteTimeout() {
    return writeTimeout;
  }

  public void setWriteTimeout(final @Nullable Duration writeTimeout) {
    this.writeTimeout = writeTimeout;
  }

  public @Nullable DataSize getMaxReadRate() {
    return maxReadRate;
  }

  public void setMaxReadRate(final @Nullable DataSize maxReadRate) {
    this.maxReadRate = maxReadRate;
  }

  public interface BackupStoreFactory {
    /**
     * Creates a store that does not limit how fast it reads the files of a backup. Use it for
     * stores that do not take backups.
     *
     * @return {@code null} for {@link BackupCfg.BackupStoreType#NONE}
     */
    @Nullable
    static BackupStore createStore(final BackupCfg backupCfg) {
      return createStore(backupCfg, BackupReadRateLimiter.unlimited());
    }

    /**
     * @param readRateLimiter the limiter of this broker, see {@link #createReadRateLimiter}
     * @return {@code null} for {@link BackupCfg.BackupStoreType#NONE}
     */
    @Nullable
    static BackupStore createStore(
        final BackupCfg backupCfg, final BackupReadRateLimiter readRateLimiter) {
      return switch (backupCfg.getStore()) {
        case NONE -> null;
        case S3 ->
            S3BackupStore.of(
                S3BackupStoreConfig.toStoreConfig(
                    backupCfg.getS3(), backupCfg.getReadTimeout(), backupCfg.getWriteTimeout()),
                readRateLimiter);
        case GCS ->
            GcsBackupStore.of(
                GcsBackupStoreConfig.toStoreConfig(
                    backupCfg.getGcs(), backupCfg.getReadTimeout(), backupCfg.getWriteTimeout()));
        case AZURE ->
            AzureBackupStore.of(
                AzureBackupStoreConfig.toStoreConfig(
                    backupCfg.getAzure(), backupCfg.getReadTimeout(), backupCfg.getWriteTimeout()));
        case FILESYSTEM ->
            FilesystemBackupStore.of(
                FilesystemBackupStoreConfig.toStoreConfig(backupCfg.getFilesystem()));
      };
    }

    /**
     * Creates the limiter for {@link BackupCfg#getMaxReadRate()}. All partitions of a broker read
     * from the same disk, whichever physical tenant they belong to, so create it once per broker
     * and pass it to every store the broker creates; each call returns a limiter with its own
     * budget. Only the S3 store limits its reads, so the limiter is unlimited unless the broker or
     * one of its physical tenants backs up to S3.
     *
     * @param backupCfg the backup configuration of the broker, which sets the read rate for all
     *     physical tenants
     * @param physicalTenantBackups the backup configurations of the physical tenants, which may use
     *     other stores than the broker's
     */
    static BackupReadRateLimiter createReadRateLimiter(
        final BackupCfg backupCfg, final Collection<BackupCfg> physicalTenantBackups) {
      final var maxReadRate = backupCfg.getMaxReadRate();
      if (maxReadRate == null) {
        return BackupReadRateLimiter.unlimited();
      }

      final var backups = new ArrayList<BackupCfg>(physicalTenantBackups.size() + 1);
      backups.add(backupCfg);
      backups.addAll(physicalTenantBackups);
      final var notLimitedStores = EnumSet.noneOf(BackupStoreType.class);
      backups.stream()
          .map(BackupCfg::getStore)
          .filter(store -> store != BackupStoreType.NONE && store != BackupStoreType.S3)
          .forEach(notLimitedStores::add);
      if (!notLimitedStores.isEmpty()) {
        Loggers.SYSTEM_LOGGER.warn(
            "Backup maxReadRate is set to {} per second, but only the S3 backup store limits its reads. Backups to {} are not limited.",
            maxReadRate,
            notLimitedStores);
      }

      final var s3Backups =
          backups.stream().filter(backup -> backup.getStore() == BackupStoreType.S3).toList();
      if (s3Backups.isEmpty()) {
        return BackupReadRateLimiter.unlimited();
      }
      final var limiter = BackupReadRateLimiter.of(maxReadRate.toBytes());
      Loggers.SYSTEM_LOGGER.info(
          "Limiting the backup reads of this broker to {} per second", maxReadRate);
      s3Backups.stream()
          .map(BackupCfg::getWriteTimeout)
          .filter(Objects::nonNull)
          .distinct()
          .forEach(BackupStoreFactory::warnIfWriteTimeoutIsTooShort);
      return limiter;
    }

    /**
     * A limited upload writes nothing while it waits for the read limit, and the S3 client aborts
     * an upload that writes nothing for its write timeout.
     */
    private static void warnIfWriteTimeoutIsTooShort(final Duration writeTimeout) {
      if (writeTimeout.compareTo(BackupReadRateLimiter.MAX_READ_PAUSE) <= 0) {
        Loggers.SYSTEM_LOGGER.warn(
            "The backup writeTimeout of {} is not longer than the {} an upload may pause while it waits for the backup read limit. The S3 client may abort such uploads; increase the write timeout.",
            writeTimeout,
            BackupReadRateLimiter.MAX_READ_PAUSE);
      }
    }
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
