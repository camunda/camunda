/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class BatchOperationDbModel implements Copyable<BatchOperationDbModel> {

  private String batchOperationKey;
  private BatchOperationState state;
  private BatchOperationType operationType;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Integer operationsTotalCount;
  private Integer operationsFailedCount;
  private Integer operationsCompletedCount;
  private List<BatchOperationErrorDbModel> errors = new ArrayList<>();

  public BatchOperationDbModel(
      final String batchOperationKey,
      final BatchOperationState state,
      final BatchOperationType operationType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final Integer operationsTotalCount,
      final Integer operationsFailedCount,
      final Integer operationsCompletedCount) {
    this.batchOperationKey = batchOperationKey;
    this.state = state;
    this.operationType = operationType;
    this.startDate = startDate;
    this.endDate = endDate;
    this.operationsTotalCount = operationsTotalCount;
    this.operationsFailedCount = operationsFailedCount;
    this.operationsCompletedCount = operationsCompletedCount;
  }

  public BatchOperationDbModel(
      final String batchOperationKey,
      final BatchOperationState state,
      final BatchOperationType operationType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final Integer operationsTotalCount,
      final Integer operationsFailedCount,
      final Integer operationsCompletedCount,
      final List<BatchOperationErrorDbModel> errors) {
    this.batchOperationKey = batchOperationKey;
    this.state = state;
    this.operationType = operationType;
    this.startDate = startDate;
    this.endDate = endDate;
    this.operationsTotalCount = operationsTotalCount;
    this.operationsFailedCount = operationsFailedCount;
    this.operationsCompletedCount = operationsCompletedCount;
    this.errors = errors != null ? errors : new ArrayList<>();
  }

  @Override
  public BatchOperationDbModel copy(
      final Function<ObjectBuilder<BatchOperationDbModel>, ObjectBuilder<BatchOperationDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  // Methods without get/set prefix

  public String batchOperationKey() {
    return batchOperationKey;
  }

  public void batchOperationKey(final String batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
  }

  public BatchOperationState state() {
    return state;
  }

  public void state(final BatchOperationState state) {
    this.state = state;
  }

  public BatchOperationType operationType() {
    return operationType;
  }

  public void operationType(final BatchOperationType operationType) {
    this.operationType = operationType;
  }

  public OffsetDateTime startDate() {
    return startDate;
  }

  public void startDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime endDate() {
    return endDate;
  }

  public void endDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public Integer operationsTotalCount() {
    return operationsTotalCount;
  }

  public void operationsTotalCount(final Integer operationsTotalCount) {
    this.operationsTotalCount = operationsTotalCount;
  }

  public Integer operationsFailedCount() {
    return operationsFailedCount;
  }

  public void operationsFailedCount(final Integer operationsFailedCount) {
    this.operationsFailedCount = operationsFailedCount;
  }

  public Integer operationsCompletedCount() {
    return operationsCompletedCount;
  }

  public void operationsCompletedCount(final Integer operationsCompletedCount) {
    this.operationsCompletedCount = operationsCompletedCount;
  }

  public List<BatchOperationErrorDbModel> errors() {
    return errors;
  }

  public void errors(final List<BatchOperationErrorDbModel> errors) {
    this.errors = errors;
  }

  public Builder toBuilder() {
    return new Builder()
        .batchOperationKey(batchOperationKey)
        .state(state)
        .operationType(operationType)
        .startDate(startDate)
        .endDate(endDate)
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
          operationsTotalCount,
          operationsFailedCount,
          operationsCompletedCount,
          errors);
    }
  }

  public static class BatchOperationErrorDbModel {
    private Integer partitionId;
    private String type;
    private String message;

    public BatchOperationErrorDbModel() {}

    public Integer partitionId() {
      return partitionId;
    }

    public void partitionId(final Integer partitionId) {
      this.partitionId = partitionId;
    }

    public String type() {
      return type;
    }

    public void type(final String type) {
      this.type = type;
    }

    public String message() {
      return message;
    }

    public void message(final String message) {
      this.message = message;
    }
  }
}
