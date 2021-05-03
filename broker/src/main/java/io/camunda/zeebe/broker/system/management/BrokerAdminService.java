/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.management;

import java.util.Map;

public interface BrokerAdminService {

  /** Request a partition to pause its StreamProcessor */
  void pauseStreamProcessing();

  /** Request a partition to resume its StreamProcessor */
  void resumeStreamProcessing();

  /** Request a partition to pause exporting */
  void pauseExporting();

  /** Request a partition to resume exporting */
  void resumeExporting();

  /**
   * Trigger a snapshot. Partition will attempt to take a snapshot instead of waiting for the
   * snapshot interval.
   */
  void takeSnapshot();

  /**
   * Prepares for upgrade by pausing stream processors and triggering snapshots. It is not normally
   * required to call this before every upgrade. However, this is useful as an upgrade procedure to
   * mitigate the effects of some known bugs.
   */
  void prepareForUpgrade();

  /**
   * Returns {@link PartitionStatus} of all partitions running on this broker.
   *
   * @return a map of partition id and partition status
   */
  Map<Integer, PartitionStatus> getPartitionStatus();
}
