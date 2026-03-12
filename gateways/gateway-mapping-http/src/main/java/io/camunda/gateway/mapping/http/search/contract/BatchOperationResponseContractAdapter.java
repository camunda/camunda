/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationErrorStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.AuditLogActorTypeEnum;
import io.camunda.gateway.protocol.model.BatchOperationStateEnum;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
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

  public static GeneratedBatchOperationResponseStrictContract adapt(
      final BatchOperationEntity entity) {
    return GeneratedBatchOperationResponseStrictContract.builder()
        .batchOperationKey(entity.batchOperationKey(), ContractPolicy.requiredNonNull())
        .state(
            ContractPolicy.mapEnum(entity.state(), BatchOperationStateEnum::fromValue),
            ContractPolicy.requiredNonNull())
        .batchOperationType(
            ContractPolicy.mapEnum(entity.operationType(), BatchOperationTypeEnum::fromValue),
            ContractPolicy.requiredNonNull())
        .operationsTotalCount(entity.operationsTotalCount(), ContractPolicy.requiredNonNull())
        .operationsFailedCount(entity.operationsFailedCount(), ContractPolicy.requiredNonNull())
        .operationsCompletedCount(
            entity.operationsCompletedCount(), ContractPolicy.requiredNonNull())
        .errors(
            ContractPolicy.nullToEmptyList(entity.errors()).stream()
                .map(BatchOperationResponseContractAdapter::toBatchOperationError)
                .toList(),
            ContractPolicy.requiredNonNull())
        .startDate(formatDate(entity.startDate()))
        .endDate(formatDate(entity.endDate()))
        .actorType(ContractPolicy.mapEnum(entity.actorType(), AuditLogActorTypeEnum::fromValue))
        .actorId(entity.actorId())
        .build();
  }

  private static GeneratedBatchOperationErrorStrictContract toBatchOperationError(
      final BatchOperationEntity.BatchOperationErrorEntity batchOperationErrorEntity) {
    return GeneratedBatchOperationErrorStrictContract.builder()
        .partitionId(batchOperationErrorEntity.partitionId(), ContractPolicy.requiredNonNull())
        .type(batchOperationErrorEntity.type(), ContractPolicy.requiredNonNull())
        .message(batchOperationErrorEntity.message(), ContractPolicy.requiredNonNull())
        .build();
  }
}
