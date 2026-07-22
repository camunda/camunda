/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record BatchOperationDbModel(
    String batchOperationKey,
    BatchOperationState state,
    BatchOperationType operationType,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    // The type/id of the actor that performed the operation. @since 8.9.0 -- null for older
    // versions.
    AuditLogActorType actorType,
    String actorId,
    Integer operationsTotalCount,
    Integer operationsFailedCount,
    Integer operationsCompletedCount,
    List<BatchOperationErrorDbModel> errors)
    implements Copyable<BatchOperationDbModel> {

  public BatchOperationDbModel {
    // Must stay mutable: MyBatis appends to this via <collection> after construction.
    errors = errors != null ? errors : new ArrayList<>();
  }

  // Matches BatchOperationResultMap's <constructor>, which omits errors -- populated separately
  // via the sibling <collection> element.
  public BatchOperationDbModel(
      final String batchOperationKey,
      final BatchOperationState state,
      final BatchOperationType operationType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final AuditLogActorType actorType,
      final String actorId,
      final Integer operationsTotalCount,
      final Integer operationsFailedCount,
      final Integer operationsCompletedCount) {
    this(
        batchOperationKey,
        state,
        operationType,
        startDate,
        endDate,
        actorType,
        actorId,
        operationsTotalCount,
        operationsFailedCount,
        operationsCompletedCount,
        null);
  }

  @Override
  public BatchOperationDbModel copy(
      final Function<ObjectBuilder<BatchOperationDbModel>, ObjectBuilder<BatchOperationDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .batchOperationKey(batchOperationKey)
        .state(state)
        .operationType(operationType)
        .startDate(startDate)
        .endDate(endDate)
        .actorType(actorType)
        .actorId(actorId)
        .operationsTotalCount(operationsTotalCount)
        .operationsFailedCount(operationsFailedCount)
        .operationsCompletedCount(operationsCompletedCount)
        .errors(errors);
  }

  public static class Builder implements ObjectBuilder<BatchOperationDbModel> {
    private String batchOperationKey;
    private BatchOperationState state;
    private BatchOperationType operationType;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private AuditLogActorType actorType;
    private String actorId;
    private Integer operationsTotalCount = 0;
    private Integer operationsFailedCount = 0;
    private Integer operationsCompletedCount = 0;
    private List<BatchOperationErrorDbModel> errors = new ArrayList<>();

    public Builder() {}

    public static BatchOperationDbModel of(
        final Function<Builder, ObjectBuilder<BatchOperationDbModel>> fn) {
      return fn.apply(new Builder()).build();
    }

    public Builder batchOperationKey(final String batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    public Builder state(final BatchOperationState state) {
      this.state = state;
      return this;
    }

    public Builder operationType(final BatchOperationType operationType) {
      this.operationType = operationType;
      return this;
    }

    public Builder startDate(final OffsetDateTime startDate) {
      this.startDate = startDate;
      return this;
    }

    public Builder endDate(final OffsetDateTime endDate) {
      this.endDate = endDate;
      return this;
    }

    public Builder actorType(final AuditLogActorType actorType) {
      this.actorType = actorType;
      return this;
    }

    public Builder actorId(final String actorId) {
      this.actorId = actorId;
      return this;
    }

    public Builder operationsTotalCount(final Integer operationsTotalCount) {
      this.operationsTotalCount = operationsTotalCount;
      return this;
    }

    public Builder operationsFailedCount(final Integer operationsFailedCount) {
      this.operationsFailedCount = operationsFailedCount;
      return this;
    }

    public Builder operationsCompletedCount(final Integer operationsCompletedCount) {
      this.operationsCompletedCount = operationsCompletedCount;
      return this;
    }

    public Builder errors(final List<BatchOperationErrorDbModel> errors) {
      this.errors = errors;
      return this;
    }

    @Override
    public BatchOperationDbModel build() {
      return new BatchOperationDbModel(
          batchOperationKey,
          state,
          operationType,
          startDate,
          endDate,
          actorType,
          actorId,
          operationsTotalCount,
          operationsFailedCount,
          operationsCompletedCount,
          errors);
    }
  }

  public record BatchOperationErrorDbModel(Integer partitionId, String type, String message) {}
}
