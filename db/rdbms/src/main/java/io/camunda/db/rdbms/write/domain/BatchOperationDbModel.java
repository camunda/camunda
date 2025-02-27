package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public record BatchOperationDbModel(
    Long batchOperationKey,
    String operationType,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    int operationsTotalCount,
    int operationsFailedCount,
    int operationsCompletedCount) {

  public static class Builder
      implements ObjectBuilder<BatchOperationDbModel> {

    private Long batchOperationKey;
    private String operationType;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private int operationsTotalCount;
    private int operationsFailedCount;
    private int operationsCompletedCount;

    // Public constructor to initialize the builder
    public Builder() {}

    // Builder methods for each field
    public Builder batchOperationKey(final Long batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
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

    // Build method to create the record
    @Override
    public BatchOperationDbModel build() {
      return new BatchOperationDbModel(
          batchOperationKey,
          operationType,
          startDate,
          endDate,
          operationsTotalCount,
          operationsFailedCount,
          operationsCompletedCount);
    }
  }
}
