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
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchOperationEntity(
    // To be backwards compatible with legacy batch operations from Operate, we need a String Key
    // Operate BatchOperation Key is a UUID
    // Engine BatchOperation Key is a Long
    String batchOperationKey,
    BatchOperationState state,
    BatchOperationType operationType,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    Integer operationsTotalCount,
    Integer operationsFailedCount,
    Integer operationsCompletedCount,
    List<BatchOperationErrorEntity> errors) {

  /**
   * Because of backwards compatibility (Legacy Batches have a UUID as ID), batchOperationKey is a
   * String. However, the new batch engine uses a Long as ID (Key).
   *
   * @param batchOperationKey throws IllegalArgumentException if the batchOperationKey is not a
   *     valid number (Legacy Batch Operation IDs are not supported)
   * @return batchOperationKey
   */
  public static Long getBatchOperationKey(final String batchOperationKey) {
    try {
      return Long.valueOf(batchOperationKey);
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format(
              "Batch operation id '%s' is not a valid number. Legacy Batch Operation IDs are not supported!",
              batchOperationKey),
          e);
    }
  }

  public record BatchOperationItemEntity(
      // To be backwards compatible with legacy batch operations from Operate, we need a String Key
      // Operate BatchOperation Key is a UUID
      // Engine BatchOperation Key is a Long
      String batchOperationKey,
      Long itemKey,
      Long processInstanceKey,
      BatchOperationItemState state,
      OffsetDateTime processedDate,
      String errorMessage) {}

  public record BatchOperationErrorEntity(Integer partitionId, String type, String message) {}

  public enum BatchOperationState {
    CREATED,
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    PARTIALLY_COMPLETED,
    CANCELED
  }

  public enum BatchOperationItemState {
    ACTIVE,
    COMPLETED,
    SKIPPED,
    CANCELED,
    FAILED
  }
}
