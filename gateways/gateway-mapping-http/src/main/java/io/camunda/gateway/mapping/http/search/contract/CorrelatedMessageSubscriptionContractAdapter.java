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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionResult;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import java.util.List;

public final class CorrelatedMessageSubscriptionContractAdapter {

  private CorrelatedMessageSubscriptionContractAdapter() {}

  public static List<CorrelatedMessageSubscriptionResult> adapt(
      final List<CorrelatedMessageSubscriptionEntity> entities) {
    return entities.stream().map(CorrelatedMessageSubscriptionContractAdapter::adapt).toList();
  }

  public static CorrelatedMessageSubscriptionResult adapt(
      final CorrelatedMessageSubscriptionEntity entity) {
    return new CorrelatedMessageSubscriptionResult()
        .correlationTime(
            ContractPolicy.requireNonNull(
                formatDate(entity.correlationTime()), "correlationTime", entity))
        .elementId(ContractPolicy.requireNonNull(entity.flowNodeId(), "elementId", entity))
        .messageKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.messageKey()), "messageKey", entity))
        .messageName(ContractPolicy.requireNonNull(entity.messageName(), "messageName", entity))
        .partitionId(ContractPolicy.requireNonNull(entity.partitionId(), "partitionId", entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), "processDefinitionId", entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .subscriptionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.subscriptionKey()), "subscriptionKey", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .correlationKey(entity.correlationKey())
        .elementInstanceKey(KeyUtil.keyToString(entity.flowNodeInstanceKey()))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()));
  }
}
