package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Set;

public class BatchOperationDbModel {

  private Long batchOperationKey;
  private BatchOperationState state;
  private String operationType;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private int operationsTotalCount;
  private int operationsFailedCount;
  private int operationsCompletedCount;

  public BatchOperationDbModel(final Long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
  }

  public BatchOperationDbModel(
      Long batchOperationKey,
      BatchOperationState state,
      String operationType,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      int operationsTotalCount,
      int operationsFailedCount,
      int operationsCompletedCount) {
    this.batchOperationKey = batchOperationKey;
    this.state = state;
    this.operationType = operationType;
    this.startDate = startDate;
    this.endDate = endDate;
    this.operationsTotalCount = operationsTotalCount;
    this.operationsFailedCount = operationsFailedCount;
    this.operationsCompletedCount = operationsCompletedCount;
  }

  // Methods without set/get prefix
  public Long batchOperationKey() {
    return batchOperationKey;
  }

  public void batchOperationKey(Long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
  }

  public BatchOperationState state() {
    return state;
  }

  public void state(BatchOperationState state) {
    this.state = state;
  }

  public String operationType() {
    return operationType;
  }

  public void operationType(String operationType) {
    this.operationType = operationType;
  }

  public OffsetDateTime startDate() {
    return startDate;
  }

  public void startDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime endDate() {
    return endDate;
  }

  public void endDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public int operationsTotalCount() {
    return operationsTotalCount;
  }

  public void operationsTotalCount(int operationsTotalCount) {
    this.operationsTotalCount = operationsTotalCount;
  }

  public int operationsFailedCount() {
    return operationsFailedCount;
  }

  public void operationsFailedCount(int operationsFailedCount) {
    this.operationsFailedCount = operationsFailedCount;
  }

  public int operationsCompletedCount() {
    return operationsCompletedCount;
  }

  public void operationsCompletedCount(int operationsCompletedCount) {
    this.operationsCompletedCount = operationsCompletedCount;
  }

  // Builder class
  public static class Builder implements ObjectBuilder<BatchOperationDbModel> {

    private Long batchOperationKey;
    private BatchOperationState state;
    private String operationType;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private int operationsTotalCount;
    private int operationsFailedCount;
    private int operationsCompletedCount;

    public Builder() {}

    public Builder batchOperationKey(Long batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    public Builder state(BatchOperationState state) {
      this.state = state;
      return this;
    }

    public Builder operationType(String operationType) {
      this.operationType = operationType;
      return this;
    }

    public Builder startDate(OffsetDateTime startDate) {
      this.startDate = startDate;
      return this;
    }

    public Builder endDate(OffsetDateTime endDate) {
      this.endDate = endDate;
      return this;
    }

    public Builder operationsTotalCount(int operationsTotalCount) {
      this.operationsTotalCount = operationsTotalCount;
      return this;
    }

    public Builder operationsFailedCount(int operationsFailedCount) {
      this.operationsFailedCount = operationsFailedCount;
      return this;
    }

    public Builder operationsCompletedCount(int operationsCompletedCount) {
      this.operationsCompletedCount = operationsCompletedCount;
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
          operationsCompletedCount);
    }
  }
}
