/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public record BatchOperationDbModel(
    String batchOperationId,
    BatchOperationState state,
    String operationType,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    Integer operationsTotalCount,
    Integer operationsFailedCount,
    Integer operationsCompletedCount) {

  // Builder class
  public static class Builder implements ObjectBuilder<BatchOperationDbModel> {

    private String batchOperationId;
    private BatchOperationState state;
    private String operationType;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate = null;
    private Integer operationsTotalCount = 0;
    private Integer operationsFailedCount = 0;
    private Integer operationsCompletedCount = 0;

    public Builder() {}

    public Builder batchOperationId(final String batchOperationId) {
      this.batchOperationId = batchOperationId;
      return this;
    }

    public Builder state(final BatchOperationState state) {
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
          batchOperationId,
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
