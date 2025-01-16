/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;

public class ProcessInstanceEntityTransformer
    implements ServiceTransformer<ProcessInstanceForListViewEntity, ProcessInstanceEntity> {

  @Override
  public ProcessInstanceEntity apply(final ProcessInstanceForListViewEntity source) {
    return new ProcessInstanceEntity(
        source.getKey(),
        source.getBpmnProcessId(),
        source.getProcessName(),
        source.getProcessVersion(),
        source.getProcessVersionTag(),
        source.getProcessDefinitionKey(),
        source.getParentProcessInstanceKey(),
        source.getParentFlowNodeInstanceKey(),
        source.getStartDate(),
        source.getEndDate(),
        toState(source.getState()),
        source.isIncident(),
        source.getTenantId());
  }

  private ProcessInstanceState toState(
      final io.camunda.webapps.schema.entities.listview.ProcessInstanceState source) {
    if (source == null) {
      return null;
    }
    switch (source) {
      case ACTIVE:
        return ProcessInstanceState.ACTIVE;
      case COMPLETED:
        return ProcessInstanceState.COMPLETED;
      case CANCELED:
        return ProcessInstanceState.CANCELED;
      default:
        throw new IllegalArgumentException("Unknown process instance state: " + source);
    }
  }
}
