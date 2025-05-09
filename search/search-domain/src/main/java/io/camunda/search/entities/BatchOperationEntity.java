/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchOperationEntity(
    // To be backwards compatible with legacy batch operations from Operate, we need a String ID
    // Operate BatchOperation ID is a UUID
    // Engine BatchOperation ID is a Long
    String batchOperationId,
    BatchOperationState state,
    String operationType,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    Integer operationsTotalCount,
    Integer operationsFailedCount,
    Integer operationsCompletedCount) {

  /**
   * Because of backwards compatibility (Legacy Batches have a UUID as ID), batchOperationId is a
   * String. However, the new batch engine uses a Long as ID (Key).
   *
   * @param batchOperationId throws IllegalArgumentException if the batchOperationId is not a valid
   *     number (Legacy Batch Operation IDs are not supported)
   * @return batchOperationKey
   */
  public static Long getBatchOperationKey(final String batchOperationId) {
    try {
      return Long.valueOf(batchOperationId);
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format(
              "Batch operation id '%s' is not a valid number. Legacy Batch Operation IDs are not supported!",
              batchOperationId),
          e);
    }
  }

  public record BatchOperationItemEntity(
      // To be backwards compatible with legacy batch operations from Operate, we need a String ID
      // Operate BatchOperation ID is a UUID
      // Engine BatchOperation ID is a Long
      String batchOperationId,
      Long itemKey,
      Long processInstanceKey,
      BatchOperationItemState state,
      OffsetDateTime processedDate,
      String errorMessage) {}

  public enum BatchOperationState {
    CREATED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    CANCELED,
    INCOMPLETED // This is just used for running legacy batch operations
  }

  public enum BatchOperationItemState {
    ACTIVE,
    COMPLETED,
    CANCELED,
    FAILED
  }
}
