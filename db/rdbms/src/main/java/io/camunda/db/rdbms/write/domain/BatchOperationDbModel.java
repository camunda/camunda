package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public record BatchOperationDbModel(
    Long batchOperationKey,
    String operationType,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    Long operationsTotalCount,
    Long operationsFailedCount,
    Long operationsCompletedCount) {

  public static class BatchOperationDbModelBuilder
      implements ObjectBuilder<BatchOperationDbModel> {

    private Long batchOperationKey;
    private String operationType;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private Long operationsTotalCount;
    private Long operationsFailedCount;
    private Long operationsCompletedCount;

    // Public constructor to initialize the builder
    public BatchOperationDbModelBuilder() {}

    // Builder methods for each field
    public BatchOperationDbModelBuilder batchOperationKey(final Long batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    public BatchOperationDbModelBuilder operationType(final String operationType) {
      this.operationType = operationType;
      return this;
    }

    public BatchOperationDbModelBuilder startDate(final OffsetDateTime startDate) {
      this.startDate = startDate;
      return this;
    }

    public BatchOperationDbModelBuilder endDate(final OffsetDateTime endDate) {
      this.endDate = endDate;
      return this;
    }

    public BatchOperationDbModelBuilder operationsTotalCount(final Long operationsTotalCount) {
      this.operationsTotalCount = operationsTotalCount;
      return this;
    }

    public BatchOperationDbModelBuilder operationsFailedCount(final Long operationsFailedCount) {
      this.operationsFailedCount = operationsFailedCount;
      return this;
    }

    public BatchOperationDbModelBuilder operationsCompletedCount(final Long operationsCompletedCount) {
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
