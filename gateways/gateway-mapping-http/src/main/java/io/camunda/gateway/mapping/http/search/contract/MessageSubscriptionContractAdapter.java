/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.MessageSubscriptionContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.MessageSubscriptionContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MessageSubscriptionStateEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.MessageSubscriptionEntity;
import java.util.List;

public final class MessageSubscriptionContractAdapter {

  private MessageSubscriptionContractAdapter() {}

  public static List<MessageSubscriptionContract> adapt(
      final List<MessageSubscriptionEntity> entities) {
    return entities.stream().map(MessageSubscriptionContractAdapter::adapt).toList();
  }

  public static MessageSubscriptionContract adapt(final MessageSubscriptionEntity entity) {
    return MessageSubscriptionContract.builder()
        .messageSubscriptionKey(
            ContractPolicy.requireNonNull(
                entity.messageSubscriptionKey(), Fields.MESSAGE_SUBSCRIPTION_KEY, entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), Fields.PROCESS_DEFINITION_ID, entity))
        .elementId(ContractPolicy.requireNonNull(entity.flowNodeId(), Fields.ELEMENT_ID, entity))
        .messageSubscriptionState(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.messageSubscriptionState(), MessageSubscriptionStateEnum::fromValue),
                Fields.MESSAGE_SUBSCRIPTION_STATE,
                entity))
        .lastUpdatedDate(
            ContractPolicy.requireNonNull(
                formatDate(entity.dateTime()), Fields.LAST_UPDATED_DATE, entity))
        .messageName(
            ContractPolicy.requireNonNull(entity.messageName(), Fields.MESSAGE_NAME, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .processDefinitionKey(entity.processDefinitionKey())
        .processInstanceKey(entity.processInstanceKey())
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .elementInstanceKey(entity.flowNodeInstanceKey())
        .correlationKey(entity.correlationKey())
        .build();
  }
}
