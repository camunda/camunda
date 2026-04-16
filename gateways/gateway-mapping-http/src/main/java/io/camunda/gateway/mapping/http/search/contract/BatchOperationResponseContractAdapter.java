/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.AuditLogActorTypeEnum;
import io.camunda.gateway.protocol.model.BatchOperationError;
import io.camunda.gateway.protocol.model.BatchOperationResponse;
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

  public static BatchOperationResponse adapt(final BatchOperationEntity entity) {
    return new BatchOperationResponse()
        .batchOperationKey(
            ContractPolicy.requireNonNull(entity.batchOperationKey(), "batchOperationKey", entity))
        .state(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.state(), BatchOperationStateEnum::fromValue),
                "state",
                entity))
        .batchOperationType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.operationType(), BatchOperationTypeEnum::fromValue),
                "batchOperationType",
                entity))
        .operationsTotalCount(
            ContractPolicy.requireNonNull(
                entity.operationsTotalCount(), "operationsTotalCount", entity))
        .operationsFailedCount(
            ContractPolicy.requireNonNull(
                entity.operationsFailedCount(), "operationsFailedCount", entity))
        .operationsCompletedCount(
            ContractPolicy.requireNonNull(
                entity.operationsCompletedCount(), "operationsCompletedCount", entity))
        .errors(
            ContractPolicy.requireNonNull(
                ContractPolicy.nullToEmptyList(entity.errors()).stream()
                    .map(BatchOperationResponseContractAdapter::toBatchOperationError)
                    .toList(),
                "errors",
                entity))
        .startDate(formatDate(entity.startDate()))
        .endDate(formatDate(entity.endDate()))
        .actorType(ContractPolicy.mapEnum(entity.actorType(), AuditLogActorTypeEnum::fromValue))
        .actorId(entity.actorId());
  }

  private static BatchOperationError toBatchOperationError(
      final BatchOperationEntity.BatchOperationErrorEntity batchOperationErrorEntity) {
    return new BatchOperationError()
        .partitionId(
            ContractPolicy.requireNonNull(
                batchOperationErrorEntity.partitionId(), "partitionId", batchOperationErrorEntity))
        .type(
            ContractPolicy.requireNonNull(
                batchOperationErrorEntity.type(), "type", batchOperationErrorEntity))
        .message(
            ContractPolicy.requireNonNull(
                batchOperationErrorEntity.message(), "message", batchOperationErrorEntity));
  }
}
