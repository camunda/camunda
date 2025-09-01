/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventType;

public class MessageSubscriptionEntityTransformer
    implements ServiceTransformer<EventEntity, MessageSubscriptionEntity> {

  @Override
  public MessageSubscriptionEntity apply(final EventEntity value) {
    return new MessageSubscriptionEntity(
        value.getKey(),
        value.getBpmnProcessId(),
        value.getProcessDefinitionKey(),
        value.getProcessInstanceKey(),
        value.getFlowNodeId(),
        value.getFlowNodeInstanceKey(),
        toMessageSubscriptionType(value.getEventType()),
        value.getDateTime(),
        value.getMetadata().getMessageName(),
        value.getMetadata().getCorrelationKey(),
        value.getTenantId());
  }

  private MessageSubscriptionType toMessageSubscriptionType(final EventType value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case CREATED -> MessageSubscriptionType.CREATED;
      case MIGRATED -> MessageSubscriptionType.MIGRATED;
      default -> throw new IllegalArgumentException("Unknown EventType: " + value);
    };
  }
}
