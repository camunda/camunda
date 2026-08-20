/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.scheduler.AsyncClosable;
import java.util.function.LongSupplier;

public interface DiskSpaceUsageMonitor extends AsyncClosable {

  void addDiskUsageListener(DiskSpaceUsageListener listener);

  void removeDiskUsageListener(DiskSpaceUsageListener listener);

  // Used only for testing
  void setFreeDiskSpaceSupplier(LongSupplier freeDiskSpaceSupplier);
}
