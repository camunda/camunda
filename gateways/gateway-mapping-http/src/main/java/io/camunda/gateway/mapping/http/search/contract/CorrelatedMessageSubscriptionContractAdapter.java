/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.CorrelatedMessageSubscriptionContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.CorrelatedMessageSubscriptionContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import java.util.List;

public final class CorrelatedMessageSubscriptionContractAdapter {

  private CorrelatedMessageSubscriptionContractAdapter() {}

  public static List<CorrelatedMessageSubscriptionContract> adapt(
      final List<CorrelatedMessageSubscriptionEntity> entities) {
    return entities.stream().map(CorrelatedMessageSubscriptionContractAdapter::adapt).toList();
  }

  public static CorrelatedMessageSubscriptionContract adapt(
      final CorrelatedMessageSubscriptionEntity entity) {
    return CorrelatedMessageSubscriptionContract.builder()
        .correlationTime(
            ContractPolicy.requireNonNull(
                formatDate(entity.correlationTime()), Fields.CORRELATION_TIME, entity))
        .elementId(ContractPolicy.requireNonNull(entity.flowNodeId(), Fields.ELEMENT_ID, entity))
        .messageKey(ContractPolicy.requireNonNull(entity.messageKey(), Fields.MESSAGE_KEY, entity))
        .messageName(
            ContractPolicy.requireNonNull(entity.messageName(), Fields.MESSAGE_NAME, entity))
        .partitionId(
            ContractPolicy.requireNonNull(entity.partitionId(), Fields.PARTITION_ID, entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), Fields.PROCESS_DEFINITION_ID, entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.processDefinitionKey(), Fields.PROCESS_DEFINITION_KEY, entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                entity.processInstanceKey(), Fields.PROCESS_INSTANCE_KEY, entity))
        .subscriptionKey(
            ContractPolicy.requireNonNull(
                entity.subscriptionKey(), Fields.SUBSCRIPTION_KEY, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .correlationKey(entity.correlationKey())
        .elementInstanceKey(entity.flowNodeInstanceKey())
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .build();
  }
}
