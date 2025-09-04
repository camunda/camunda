/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.CorrelatedMessageEntity;
import io.camunda.webapps.schema.entities.CorrelatedMessageEntity;

public class CorrelatedMessagesEntityTransformer
    implements ServiceTransformer<CorrelatedMessageEntity, io.camunda.search.entities.CorrelatedMessageEntity> {

  @Override
  public io.camunda.search.entities.CorrelatedMessageEntity apply(final CorrelatedMessageEntity value) {
    return new io.camunda.search.entities.CorrelatedMessageEntity(
        value.getCorrelationKey(),
        value.getCorrelationTime(),
        value.getFlowNodeId(), // Maps to elementId in search domain
        value.getFlowNodeInstanceKey(), // Maps to elementInstanceKey in search domain
        value.getMessageKey(),
        value.getMessageName(),
        value.getBpmnProcessId(), // Maps to processDefinitionId in search domain
        value.getProcessDefinitionKey(),
        value.getProcessInstanceKey(),
        value.getSubscriptionKey(),
        value.getTenantId());
  }
}