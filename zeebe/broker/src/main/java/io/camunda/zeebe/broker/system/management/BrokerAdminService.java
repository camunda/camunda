/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import java.util.Map;

public interface BrokerAdminService {

  /** Request a partition to pause its StreamProcessor */
  void pauseStreamProcessing();

  /** Request a partition to resume its StreamProcessor */
  void resumeStreamProcessing();

  /** Request a partition to pause exporting */
  void pauseExporting();

  /** Request a partition to soft pause exporting */
  void softPauseExporting();

  /** Request a partition to resume exporting */
  void resumeExporting();

  /**
   * Trigger a snapshot. Partition will attempt to take a snapshot instead of waiting for the
   * snapshot interval.
   */
  void takeSnapshot();

  /**
   * Returns {@link PartitionStatus} of all partitions running on this broker.
   *
   * @return a map of partition id and partition status
   */
  Map<Integer, PartitionStatus> getPartitionStatus();
}
