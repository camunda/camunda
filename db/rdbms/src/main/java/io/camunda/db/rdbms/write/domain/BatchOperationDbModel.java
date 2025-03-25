/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationStatus;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public record BatchOperationDbModel(
    Long batchOperationKey,
    BatchOperationStatus status,
    String operationType,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    Integer operationsTotalCount,
    Integer operationsFailedCount,
    Integer operationsCompletedCount) {

  // Builder class
  public static class Builder implements ObjectBuilder<BatchOperationDbModel> {

    private Long batchOperationKey;
    private BatchOperationStatus status;
    private String operationType;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private Integer operationsTotalCount;
    private Integer operationsFailedCount;
    private Integer operationsCompletedCount;

    public Builder() {}

    public Builder batchOperationKey(final Long batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    public Builder status(final BatchOperationStatus status) {
      this.status = status;
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

    @Override
    public BatchOperationDbModel build() {
      return new BatchOperationDbModel(
          batchOperationKey,
          status,
          operationType,
          startDate,
          endDate,
          operationsTotalCount,
          operationsFailedCount,
          operationsCompletedCount);
    }
  }
}
