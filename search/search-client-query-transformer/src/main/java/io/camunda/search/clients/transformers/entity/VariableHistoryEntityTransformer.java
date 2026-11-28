/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.VariableHistoryItemEntity;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemActorType;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemOperationType;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemVariableOperationType;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryActorType;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryEntity;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryOperationType;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;

public class VariableHistoryEntityTransformer
    implements ServiceTransformer<VariableHistoryEntity, VariableHistoryItemEntity> {

  @Override
  public VariableHistoryItemEntity apply(final VariableHistoryEntity value) {
    return new VariableHistoryItemEntity(
        value.getVariableHistoryItemKey(),
        value.getVariableKey(),
        value.getName(),
        value.getValue(),
        value.getFullValue(),
        value.getIsPreview(),
        value.getScopeKey(),
        value.getElementInstanceKey(),
        value.getElementId(),
        value.getProcessInstanceKey(),
        value.getRootProcessInstanceKey(),
        value.getProcessDefinitionKey(),
        value.getProcessDefinitionId(),
        value.getTenantId(),
        value.getTimestamp(),
        mapOperationType(value.getOperationType()),
        mapVariableOperationType(value.getVariableOperationType()),
        mapActorType(value.getActorType()),
        value.getActorId(),
        value.getAgentElementId());
  }

  private VariableHistoryItemOperationType mapOperationType(
      final VariableHistoryOperationType operationType) {
    return operationType != null
        ? VariableHistoryItemOperationType.valueOf(operationType.name())
        : null;
  }

  private VariableHistoryItemVariableOperationType mapVariableOperationType(
      final VariableOperationType variableOperationType) {
    return variableOperationType != null
        ? VariableHistoryItemVariableOperationType.valueOf(variableOperationType.name())
        : null;
  }

  private VariableHistoryItemActorType mapActorType(final VariableHistoryActorType actorType) {
    return actorType != null ? VariableHistoryItemActorType.valueOf(actorType.name()) : null;
  }
}
