/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static io.camunda.zeebe.broker.Broker.LOG;

import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.scheduler.Actor;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

public class DiskSpaceUsageMonitorActor extends Actor implements DiskSpaceUsageMonitor {

  private final Set<DiskSpaceUsageListener> diskSpaceUsageListeners = new HashSet<>();
  private boolean currentDiskAvailableStatus = true;
  private LongSupplier freeDiskSpaceSupplier;
  private final Duration monitoringDelay;
  private final long minFreeDiskSpaceRequired;

  public DiskSpaceUsageMonitorActor(final DataCfg dataCfg) {
    final var diskCfg = dataCfg.getDisk();
    monitoringDelay = diskCfg.getMonitoringInterval();
    final var directory = new File(dataCfg.getRootDirectory());
    if (!directory.exists()) {
      throw new UncheckedIOException(new IOException("Folder '" + directory + "' does not exist."));
    }
    minFreeDiskSpaceRequired = diskCfg.getFreeSpace().getProcessing().toBytes();
    freeDiskSpaceSupplier = directory::getUsableSpace;
  }

  @Override
  protected void onActorStarted() {
    checkDiskUsageAndNotifyListeners();
    actor.runAtFixedRate(monitoringDelay, this::checkDiskUsageAndNotifyListeners);
  }

  private void checkDiskUsageAndNotifyListeners() {
    final long freeDiskSpaceAvailable = freeDiskSpaceSupplier.getAsLong();
    final boolean previousStatus = currentDiskAvailableStatus;
    currentDiskAvailableStatus = freeDiskSpaceAvailable >= minFreeDiskSpaceRequired;
    if (currentDiskAvailableStatus != previousStatus) {
      if (!currentDiskAvailableStatus) {
        LOG.warn(
            "Out of disk space. Current available {} bytes. Minimum needed {} bytes.",
            freeDiskSpaceAvailable,
            minFreeDiskSpaceRequired);
        diskSpaceUsageListeners.forEach(DiskSpaceUsageListener::onDiskSpaceNotAvailable);
      } else {
        LOG.info("Disk space available again. Current available {} bytes", freeDiskSpaceAvailable);
        diskSpaceUsageListeners.forEach(DiskSpaceUsageListener::onDiskSpaceAvailable);
      }
    }
  }

  @Override
  public void addDiskUsageListener(final DiskSpaceUsageListener listener) {
    actor.call(
        () -> {
          diskSpaceUsageListeners.add(listener);
          // Listeners always assumes diskspace is available on start. So notify them if it is not.
          if (!currentDiskAvailableStatus) {
            listener.onDiskSpaceNotAvailable();
          }
        });
  }

  @Override
  public void removeDiskUsageListener(final DiskSpaceUsageListener listener) {
    actor.call(() -> diskSpaceUsageListeners.remove(listener));
  }

  // Used only for testing
  @Override
  public void setFreeDiskSpaceSupplier(final LongSupplier freeDiskSpaceSupplier) {
    this.freeDiskSpaceSupplier = freeDiskSpaceSupplier;
  }
}
