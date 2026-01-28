/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.search.entities.MessageSubscriptionEntity;

public class MessageSubscriptionEntityMapper {

  public static MessageSubscriptionEntity toEntity(
      final MessageSubscriptionDbModel messageSubscriptionDbModel) {
    return MessageSubscriptionEntity.builder()
        .messageSubscriptionKey(messageSubscriptionDbModel.messageSubscriptionKey())
        .processDefinitionId(messageSubscriptionDbModel.processDefinitionId())
        .processDefinitionKey(messageSubscriptionDbModel.processDefinitionKey())
        .processInstanceKey(messageSubscriptionDbModel.processInstanceKey())
        .rootProcessInstanceKey(messageSubscriptionDbModel.rootProcessInstanceKey())
        .flowNodeId(messageSubscriptionDbModel.flowNodeId())
        .flowNodeInstanceKey(messageSubscriptionDbModel.flowNodeInstanceKey())
        .messageSubscriptionState(messageSubscriptionDbModel.messageSubscriptionState())
        .dateTime(messageSubscriptionDbModel.dateTime())
        .messageName(messageSubscriptionDbModel.messageName())
        .correlationKey(messageSubscriptionDbModel.correlationKey())
        .tenantId(messageSubscriptionDbModel.tenantId())
        .build();
  }
}
