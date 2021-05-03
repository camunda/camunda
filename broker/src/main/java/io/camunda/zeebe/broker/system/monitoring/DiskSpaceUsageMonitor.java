/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.monitoring;

import static io.zeebe.broker.Broker.LOG;

import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.util.sched.Actor;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public class DiskSpaceUsageMonitor extends Actor {

  private final List<DiskSpaceUsageListener> diskSpaceUsageListeners = new ArrayList<>();
  private boolean currentDiskAvailableStatus = true;
  private LongSupplier freeDiskSpaceSupplier;
  private final Duration monitoringDelay;
  private final long minFreeDiskSpaceRequired;

  public DiskSpaceUsageMonitor(final DataCfg dataCfg) {
    monitoringDelay = dataCfg.getDiskUsageMonitoringInterval();
    minFreeDiskSpaceRequired = dataCfg.getFreeDiskSpaceCommandWatermark();
    final var directory = new File(dataCfg.getDirectory());
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

  public void removeDiskUsageListener(final DiskSpaceUsageListener listener) {
    actor.call(() -> diskSpaceUsageListeners.remove(listener));
  }

  // Used only for testing
  public void setFreeDiskSpaceSupplier(final LongSupplier freeDiskSpaceSupplier) {
    this.freeDiskSpaceSupplier = freeDiskSpaceSupplier;
  }
}
