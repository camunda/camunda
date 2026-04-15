/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationResponseContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogActorTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationErrorContract;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationResponseContract;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationStateEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.BatchOperationEntity;

/**
 * Contract adaptation layer for BatchOperationResponse.
 *
 * <p>POC demonstration #2 for reviewers: move null/default policy out of SearchQueryResponseMapper
 * and into a hand-written adapter.
 *
 * <p>In this slice, policy normalizes null errors to an empty list while keeping deterministic
 * contract validation in the generated strict contract.
 */
public final class BatchOperationResponseContractAdapter {

  private BatchOperationResponseContractAdapter() {}

  public static BatchOperationResponseContract adapt(final BatchOperationEntity entity) {
    return BatchOperationResponseContract.builder()
        .batchOperationKey(
            ContractPolicy.requireNonNull(
                entity.batchOperationKey(), Fields.BATCH_OPERATION_KEY, entity))
        .state(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.state(), BatchOperationStateEnum::fromValue),
                Fields.STATE,
                entity))
        .batchOperationType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.operationType(), BatchOperationTypeEnum::fromValue),
                Fields.BATCH_OPERATION_TYPE,
                entity))
        .operationsTotalCount(
            ContractPolicy.requireNonNull(
                entity.operationsTotalCount(), Fields.OPERATIONS_TOTAL_COUNT, entity))
        .operationsFailedCount(
            ContractPolicy.requireNonNull(
                entity.operationsFailedCount(), Fields.OPERATIONS_FAILED_COUNT, entity))
        .operationsCompletedCount(
            ContractPolicy.requireNonNull(
                entity.operationsCompletedCount(), Fields.OPERATIONS_COMPLETED_COUNT, entity))
        .errors(
            ContractPolicy.requireNonNull(
                ContractPolicy.nullToEmptyList(entity.errors()).stream()
                    .map(BatchOperationResponseContractAdapter::toBatchOperationError)
                    .toList(),
                Fields.ERRORS,
                entity))
        .startDate(formatDate(entity.startDate()))
        .endDate(formatDate(entity.endDate()))
        .actorType(ContractPolicy.mapEnum(entity.actorType(), AuditLogActorTypeEnum::fromValue))
        .actorId(entity.actorId())
        .build();
  }

  private static BatchOperationErrorContract toBatchOperationError(
      final BatchOperationEntity.BatchOperationErrorEntity batchOperationErrorEntity) {
    return BatchOperationErrorContract.builder()
        .partitionId(
            ContractPolicy.requireNonNull(
                batchOperationErrorEntity.partitionId(),
                BatchOperationErrorContract.Fields.PARTITION_ID,
                batchOperationErrorEntity))
        .type(
            ContractPolicy.requireNonNull(
                batchOperationErrorEntity.type(),
                BatchOperationErrorContract.Fields.TYPE,
                batchOperationErrorEntity))
        .message(
            ContractPolicy.requireNonNull(
                batchOperationErrorEntity.message(),
                BatchOperationErrorContract.Fields.MESSAGE,
                batchOperationErrorEntity))
        .build();
  }
}
