/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import io.camunda.search.entities.CorrelatedMessageEntity;

public class CorrelatedMessagesEntityMapper {

  public static CorrelatedMessageEntity toEntity(final CorrelatedMessageDbModel dbModel) {
    return CorrelatedMessageEntity.builder()
        .correlationKey(dbModel.correlationKey())
        .correlationTime(dbModel.correlationTime())
        .elementId(dbModel.flowNodeId()) // Map flowNodeId to elementId
        .elementInstanceKey(dbModel.flowNodeInstanceKey()) // Map flowNodeInstanceKey to elementInstanceKey
        .messageKey(dbModel.messageKey())
        .messageName(dbModel.messageName())
        .processDefinitionId(dbModel.processDefinitionId())
        .processDefinitionKey(dbModel.processDefinitionKey())
        .processInstanceKey(dbModel.processInstanceKey())
        .subscriptionKey(dbModel.subscriptionKey())
        .tenantId(dbModel.tenantId())
        .build();
  }
}