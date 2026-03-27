/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationItemResponseStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationItemResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;

/**
 * Contract adaptation layer for BatchOperationItemResponse.
 *
 * <p>Converts {@link BatchOperationItemEntity} to a generated strict contract, applying business
 * policy for null handling and enum/key coercion.
 */
public final class BatchOperationItemResponseContractAdapter {

  private BatchOperationItemResponseContractAdapter() {}

  public static GeneratedBatchOperationItemResponseStrictContract adapt(
      final BatchOperationItemEntity entity) {
    return GeneratedBatchOperationItemResponseStrictContract.builder()
        .operationType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.operationType(), GeneratedBatchOperationTypeEnum::fromValue),
                Fields.OPERATION_TYPE,
                entity))
        .batchOperationKey(
            ContractPolicy.requireNonNull(
                entity.batchOperationKey(), Fields.BATCH_OPERATION_KEY, entity))
        .itemKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.itemKey()), Fields.ITEM_KEY, entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                entity.processInstanceKey(), Fields.PROCESS_INSTANCE_KEY, entity))
        .state(
            ContractPolicy.requireNonNull(
                entity.state() != null ? entity.state().name() : null, Fields.STATE, entity))
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .processedDate(formatDate(entity.processedDate()))
        .errorMessage(entity.errorMessage())
        .build();
  }
}
