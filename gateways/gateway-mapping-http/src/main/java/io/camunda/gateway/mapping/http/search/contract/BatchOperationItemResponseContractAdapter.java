/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.mapEnum;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;

import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.BatchOperationItemResponse;
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

  public static BatchOperationItemResponse adapt(final BatchOperationItemEntity entity) {
    return new BatchOperationItemResponse()
        .operationType(
            requireNonNull(
                mapEnum(entity.operationType(), BatchOperationTypeEnum::fromValue),
                "operationType",
                entity))
        .batchOperationKey(requireNonNull(entity.batchOperationKey(), "batchOperationKey", entity))
        .itemKey(requireNonNull(KeyUtil.keyToString(entity.itemKey()), "itemKey", entity))
        .processInstanceKey(
            requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .state(
            requireNonNull(entity.state() != null ? entity.state().name() : null, "state", entity))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .processedDate(formatDate(entity.processedDate()))
        .errorMessage(entity.errorMessage());
  }
}
