/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationItemResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
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
            ContractPolicy.mapEnum(entity.operationType(), BatchOperationTypeEnum::fromValue),
            ContractPolicy.requiredNonNull())
        .batchOperationKey(entity.batchOperationKey(), ContractPolicy.requiredNonNull())
        .itemKey(KeyUtil.keyToString(entity.itemKey()), ContractPolicy.requiredNonNull())
        .processInstanceKey(entity.processInstanceKey(), ContractPolicy.requiredNonNull())
        .state(
            entity.state() != null ? entity.state().name() : null, ContractPolicy.requiredNonNull())
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .processedDate(formatDate(entity.processedDate()))
        .errorMessage(entity.errorMessage())
        .build();
  }
}
