/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.Loggers;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.util.unit.DataSize;

public class DiskCfg implements ConfigurationEntry {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private static final boolean DEFAULT_DISK_MONITORING_ENABLED = true;
  private static final DataSize DISABLED_DISK_FREESPACE = DataSize.ofBytes(0);
  private static final Duration DEFAULT_DISK_USAGE_MONITORING_DELAY = Duration.ofSeconds(1);
  private boolean enableMonitoring = DEFAULT_DISK_MONITORING_ENABLED;
  private Duration monitoringInterval = DEFAULT_DISK_USAGE_MONITORING_DELAY;
  private FreeSpaceCfg freeSpace = new FreeSpaceCfg();
  private UsedSpaceCfg usedSpace = new UsedSpaceCfg();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    freeSpace.init(globalConfig, brokerBase);
    usedSpace.init(globalConfig, brokerBase);

    if (!enableMonitoring) {
      LOG.info(
          "Disk usage monitoring is disabled, setting required freespace to {} and removing used space limits",
          DISABLED_DISK_FREESPACE);
      freeSpace.setReplication(DISABLED_DISK_FREESPACE);
      freeSpace.setProcessing(DISABLED_DISK_FREESPACE);
      usedSpace.setProcessing(null);
      usedSpace.setReplication(null);
    }
  }

  public boolean isEnableMonitoring() {
    return enableMonitoring;
  }

  public void setEnableMonitoring(final boolean enableMonitoring) {
    this.enableMonitoring = enableMonitoring;
  }

  public Duration getMonitoringInterval() {
    return monitoringInterval;
  }

  public void setMonitoringInterval(final Duration monitoringInterval) {
    this.monitoringInterval = monitoringInterval;
  }

  public FreeSpaceCfg getFreeSpace() {
    return freeSpace;
  }

  public void setFreeSpace(final FreeSpaceCfg freeSpace) {
    this.freeSpace = freeSpace;
  }

  public UsedSpaceCfg getUsedSpace() {
    return usedSpace;
  }

  public void setUsedSpace(final UsedSpaceCfg usedSpace) {
    this.usedSpace = usedSpace;
  }

  @Override
  public String toString() {
    return "DiskCfg{"
        + "enableMonitoring="
        + enableMonitoring
        + ", freeSpace="
        + freeSpace
        + ", usedSpace="
        + usedSpace
        + '}';
  }

  public static class FreeSpaceCfg implements ConfigurationEntry {

    private static final DataSize DEFAULT_PROCESSING_FREESPACE = DataSize.ofGigabytes(2);
    private static final DataSize DEFAULT_REPLICATION_FREESPACE = DataSize.ofGigabytes(1);
    private DataSize processing = DEFAULT_PROCESSING_FREESPACE;
    private DataSize replication = DEFAULT_REPLICATION_FREESPACE;

    public DataSize getProcessing() {
      return processing;
    }

    public void setProcessing(final DataSize processing) {
      this.processing = processing;
    }

    public DataSize getReplication() {
      return replication;
    }

    public void setReplication(final DataSize replication) {
      this.replication = replication;
    }

    @Override
    public String toString() {
      return "FreeSpaceCfg{"
          + "processing='"
          + processing
          + '\''
          + ", replication='"
          + replication
          + '\''
          + '}';
    }
  }

  public static class UsedSpaceCfg implements ConfigurationEntry {

    private @Nullable DataSize processing;
    private @Nullable DataSize replication;

    @Override
    public void init(final BrokerCfg globalConfig, final String brokerBase) {
      final var segmentSize = globalConfig.getData().getLogSegmentSize();
      if (processing != null && processing.compareTo(segmentSize) <= 0) {
        throw new IllegalArgumentException(
            "Expected disk.usedSpace.processing (%s) to be greater than the log segment size (%s)"
                .formatted(processing, segmentSize));
      }
      if (replication != null && replication.compareTo(segmentSize) <= 0) {
        throw new IllegalArgumentException(
            "Expected disk.usedSpace.replication (%s) to be greater than the log segment size (%s)"
                .formatted(replication, segmentSize));
      }
      if (processing != null && replication != null && replication.compareTo(processing) <= 0) {
        throw new IllegalArgumentException(
            "Expected disk.usedSpace.replication (%s) to be greater than disk.usedSpace.processing (%s)"
                .formatted(replication, processing));
      }
    }

    public boolean isLimited() {
      return processing != null || replication != null;
    }

    public @Nullable DataSize getProcessing() {
      return processing;
    }

    public void setProcessing(final @Nullable DataSize processing) {
      this.processing = processing;
    }

    public @Nullable DataSize getReplication() {
      return replication;
    }

    public void setReplication(final @Nullable DataSize replication) {
      this.replication = replication;
    }

    @Override
    public String toString() {
      return "UsedSpaceCfg{processing=" + processing + ", replication=" + replication + '}';
    }
  }
}
