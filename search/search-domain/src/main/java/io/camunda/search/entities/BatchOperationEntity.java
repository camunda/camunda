/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchOperationEntity(
    // To be backwards compatible with legacy batch operations from Operate, we need a String Key
    // Operate BatchOperation Key is a UUID
    // Engine BatchOperation Key is a Long
    String batchOperationKey,
    @Nullable BatchOperationState state,
    @Nullable BatchOperationType operationType,
    @Nullable OffsetDateTime startDate,
    @Nullable OffsetDateTime endDate,
    /*
     * The type of the actor who started the batch operation.
     *
     * @since 8.9.0 - so null for older versions
     */
    @Nullable AuditLogActorType actorType,
    /*
     * The id of the actor who started the batch operation.
     *
     * @since 8.9.0 - so null for older versions
     */
    @Nullable String actorId,
    @Nullable Integer operationsTotalCount,
    @Nullable Integer operationsFailedCount,
    @Nullable Integer operationsCompletedCount,
    List<BatchOperationErrorEntity> errors) {

  public BatchOperationEntity {
    Objects.requireNonNull(batchOperationKey, "batchOperationKey");
    // Mutable collections are required: MyBatis hydrates collection-mapped fields (e.g. from a
    // <collection> result map or a LEFT JOIN) by calling .add() on the existing instance.
    // Immutable defaults (e.g. List.of()) would cause UnsupportedOperationException at runtime.
    errors = errors != null ? errors : new ArrayList<>();
  }

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
      @Nullable BatchOperationType operationType,
      @Nullable Long itemKey,
      @Nullable Long processInstanceKey,
      @Nullable Long rootProcessInstanceKey,
      @Nullable BatchOperationItemState state,
      @Nullable OffsetDateTime processedDate,
      @Nullable String errorMessage) {
    public BatchOperationItemEntity {
      Objects.requireNonNull(batchOperationKey, "batchOperationKey");
    }
  }

  public record BatchOperationErrorEntity(
      @Nullable Integer partitionId, @Nullable String type, @Nullable String message) {}

  public enum BatchOperationState {
    CREATED,
    ACTIVE,
    SUSPENDED,
    CANCELED,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED
  }

  public enum BatchOperationItemState {
    ACTIVE,
    COMPLETED,
    SKIPPED,
    CANCELED,
    FAILED
  }
}
