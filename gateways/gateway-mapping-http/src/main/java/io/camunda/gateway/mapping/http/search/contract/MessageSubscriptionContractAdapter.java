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
import io.camunda.gateway.protocol.model.MessageSubscriptionResult;
import io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum;
import io.camunda.search.entities.MessageSubscriptionEntity;
import java.util.List;

public final class MessageSubscriptionContractAdapter {

  private MessageSubscriptionContractAdapter() {}

  public static List<MessageSubscriptionResult> adapt(
      final List<MessageSubscriptionEntity> entities) {
    return entities.stream().map(MessageSubscriptionContractAdapter::adapt).toList();
  }

  public static MessageSubscriptionResult adapt(final MessageSubscriptionEntity entity) {
    return new MessageSubscriptionResult()
        .messageSubscriptionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.messageSubscriptionKey()),
                "messageSubscriptionKey",
                entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), "processDefinitionId", entity))
        .elementId(ContractPolicy.requireNonNull(entity.flowNodeId(), "elementId", entity))
        .messageSubscriptionState(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.messageSubscriptionState(), MessageSubscriptionStateEnum::fromValue),
                "messageSubscriptionState",
                entity))
        .lastUpdatedDate(
            ContractPolicy.requireNonNull(formatDate(entity.dateTime()), "lastUpdatedDate", entity))
        .messageName(ContractPolicy.requireNonNull(entity.messageName(), "messageName", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(entity.processInstanceKey()))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .elementInstanceKey(KeyUtil.keyToString(entity.flowNodeInstanceKey()))
        .correlationKey(entity.correlationKey());
  }
}
