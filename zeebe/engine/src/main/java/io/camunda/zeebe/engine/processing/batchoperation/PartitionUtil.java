/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.value.BatchOperationRelated;

public class PartitionUtil {
  /**
   * Returns the lead partition ID for the given batch operation record value. THe lead partition is
   * the partition the batch operation was originally created on.
   *
   * @param recordValue the batch operation record value
   * @return the lead partition ID
   */
  public static int getLeadPartition(final BatchOperationRelated recordValue) {
    return Protocol.decodePartitionId(recordValue.getBatchOperationKey());
  }

  /**
   * Checks if the given partition ID is the lead partition for the given batch operation.
   *
   * @param recordValue the batch operation record value
   * @param partitionId the partition ID to check
   * @return <code>true</code> if the partition ID is the lead partition, <code>false</code>
   *     otherwise
   */
  public static boolean isOnLeadPartition(
      final BatchOperationRelated recordValue, final int partitionId) {
    return getLeadPartition(recordValue) == partitionId;
  }
}
