/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionState;

public class MessageSubscriptionEntityTransformer
    implements ServiceTransformer<
        MessageSubscriptionEntity, io.camunda.search.entities.MessageSubscriptionEntity> {

  @Override
  public io.camunda.search.entities.MessageSubscriptionEntity apply(
      final MessageSubscriptionEntity value) {
    return io.camunda.search.entities.MessageSubscriptionEntity.builder()
        .messageSubscriptionKey(value.getKey())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .processDefinitionName(value.getProcessDefinitionName())
        .processDefinitionVersion(value.getProcessDefinitionVersion())
        .processInstanceKey(value.getProcessInstanceKey())
        .rootProcessInstanceKey(value.getRootProcessInstanceKey())
        .flowNodeId(value.getFlowNodeId())
        .flowNodeInstanceKey(value.getFlowNodeInstanceKey())
        .messageSubscriptionState(toMessageSubscriptionState(value.getEventType()))
        .messageSubscriptionType(toMessageSubscriptionType(value.getMessageSubscriptionType()))
        .dateTime(value.getDateTime())
        .messageName(value.getMetadata() != null ? value.getMetadata().getMessageName() : null)
        .correlationKey(
            value.getMetadata() != null ? value.getMetadata().getCorrelationKey() : null)
        .tenantId(value.getTenantId())
        .extensionProperties(value.getExtensionProperties())
        .build();
  }

  private io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState
      toMessageSubscriptionState(final MessageSubscriptionState value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case CORRELATED ->
          io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState.CORRELATED;
      case CREATED ->
          io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState.CREATED;
      case MIGRATED ->
          io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState.MIGRATED;
      case DELETED ->
          io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState.DELETED;
      default -> throw new IllegalArgumentException("Unknown EventType: " + value);
    };
  }

  private MessageSubscriptionType toMessageSubscriptionType(final String value) {
    if (value == null) {
      return MessageSubscriptionType.INTERMEDIATE_EVENT_SUBSCRIPTION;
    }
    try {
      return MessageSubscriptionType.valueOf(value);
    } catch (final IllegalArgumentException e) {
      return MessageSubscriptionType.INTERMEDIATE_EVENT_SUBSCRIPTION;
    }
  }
}
