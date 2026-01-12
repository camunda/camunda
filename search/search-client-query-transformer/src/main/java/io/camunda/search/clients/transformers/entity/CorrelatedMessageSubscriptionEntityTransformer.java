/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity.MessageSubscriptionType;

public class CorrelatedMessageSubscriptionEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity,
        CorrelatedMessageSubscriptionEntity> {

  @Override
  public CorrelatedMessageSubscriptionEntity apply(
      final io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity value) {
    return new CorrelatedMessageSubscriptionEntity(
        value.getCorrelationKey(),
        value.getCorrelationTime(),
        value.getFlowNodeId(),
        value.getFlowNodeInstanceKey(),
        value.getMessageKey(),
        value.getMessageName(),
        value.getPartitionId(),
        value.getBpmnProcessId(),
        value.getProcessDefinitionKey(),
        value.getProcessInstanceKey(),
        value.getRootProcessInstanceKey(),
        value.getSubscriptionKey(),
        toMessageSubscriptionType(value.getSubscriptionType()),
        value.getTenantId());
  }

  private MessageSubscriptionType toMessageSubscriptionType(final String value) {
    if (value == null) {
      return null;
    }

    return switch (value) {
      case "PROCESS_EVENT" -> MessageSubscriptionType.PROCESS_EVENT;
      case "START_EVENT" -> MessageSubscriptionType.START_EVENT;
      default -> throw new IllegalArgumentException("Unknown listener event type: " + value);
    };
  }
}
