/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
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
   * Checks if the given record is on its lead partition.
   *
   * @param record the batch operation related record
   * @return {@code true} if the record is on its lead partition, {@code false} otherwise
   */
  public static boolean isOnLeadPartition(final Record<? extends BatchOperationRelated> record) {
    return getLeadPartition(record.getValue()) == record.getPartitionId();
  }
}
