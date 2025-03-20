package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationStatus;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public class BatchOperationDbModel {

  private Long batchOperationKey;
  private BatchOperationStatus state;
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
      final Long batchOperationKey,
      final BatchOperationStatus state,
      final String operationType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final int operationsTotalCount,
      final int operationsFailedCount,
      final int operationsCompletedCount) {
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

  public void batchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
  }

  public BatchOperationStatus state() {
    return state;
  }

  public void state(final BatchOperationStatus state) {
    this.state = state;
  }

  public String operationType() {
    return operationType;
  }

  public void operationType(final String operationType) {
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

  public int operationsTotalCount() {
    return operationsTotalCount;
  }

  public void operationsTotalCount(final int operationsTotalCount) {
    this.operationsTotalCount = operationsTotalCount;
  }

  public int operationsFailedCount() {
    return operationsFailedCount;
  }

  public void operationsFailedCount(final int operationsFailedCount) {
    this.operationsFailedCount = operationsFailedCount;
  }

  public int operationsCompletedCount() {
    return operationsCompletedCount;
  }

  public void operationsCompletedCount(final int operationsCompletedCount) {
    this.operationsCompletedCount = operationsCompletedCount;
  }

  // Builder class
  public static class Builder implements ObjectBuilder<BatchOperationDbModel> {

    private Long batchOperationKey;
    private BatchOperationStatus state;
    private String operationType;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private int operationsTotalCount;
    private int operationsFailedCount;
    private int operationsCompletedCount;

    public Builder() {}

    public Builder batchOperationKey(final Long batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    public Builder state(final BatchOperationStatus state) {
      this.state = state;
      return this;
    }

    public Builder operationType(final String operationType) {
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

    public Builder operationsTotalCount(final int operationsTotalCount) {
      this.operationsTotalCount = operationsTotalCount;
      return this;
    }

    public Builder operationsFailedCount(final int operationsFailedCount) {
      this.operationsFailedCount = operationsFailedCount;
      return this;
    }

    public Builder operationsCompletedCount(final int operationsCompletedCount) {
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
