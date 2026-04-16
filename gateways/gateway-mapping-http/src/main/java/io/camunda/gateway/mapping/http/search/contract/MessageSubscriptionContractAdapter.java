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
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;

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
            requireNonNull(
                keyToString(entity.messageSubscriptionKey()), "messageSubscriptionKey", entity))
        .processDefinitionId(
            requireNonNull(entity.processDefinitionId(), "processDefinitionId", entity))
        .elementId(requireNonNull(entity.flowNodeId(), "elementId", entity))
        .messageSubscriptionState(
            requireNonNull(
                mapEnum(entity.messageSubscriptionState(), MessageSubscriptionStateEnum::fromValue),
                "messageSubscriptionState",
                entity))
        .lastUpdatedDate(requireNonNull(formatDate(entity.dateTime()), "lastUpdatedDate", entity))
        .messageName(requireNonNull(entity.messageName(), "messageName", entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .processDefinitionKey(keyToString(entity.processDefinitionKey()))
        .processInstanceKey(keyToString(entity.processInstanceKey()))
        .rootProcessInstanceKey(keyToString(entity.rootProcessInstanceKey()))
        .elementInstanceKey(keyToString(entity.flowNodeInstanceKey()))
        .correlationKey(entity.correlationKey());
  }
}
