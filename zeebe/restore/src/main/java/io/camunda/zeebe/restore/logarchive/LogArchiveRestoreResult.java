/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import java.util.Map;

/**
 * The result of restoring the local partitions of a broker.
 *
 * @param restorePoint the checkpoint all partitions were restored to
 * @param timelineId the new timeline of the restored partitions
 * @param partitions the result per restored partition, by partition id
 */
public record LogArchiveRestoreResult(
    LogArchiveRestorePoint restorePoint,
    String timelineId,
    Map<Integer, LogArchivePartitionRestoreResult> partitions) {

  public LogArchiveRestoreResult {
    partitions = Map.copyOf(partitions);
  }
}
