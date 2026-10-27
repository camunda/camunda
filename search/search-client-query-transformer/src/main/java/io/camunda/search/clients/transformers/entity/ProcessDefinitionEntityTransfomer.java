/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity.ProcessDefinitionState;
import io.camunda.webapps.schema.entities.ProcessEntity;

public class ProcessDefinitionEntityTransfomer
    implements ServiceTransformer<ProcessEntity, ProcessDefinitionEntity> {

  @Override
  public ProcessDefinitionEntity apply(final ProcessEntity value) {
    return new ProcessDefinitionEntity(
        value.getKey(),
        value.getName(),
        value.getBpmnProcessId(),
        value.getBpmnXml(),
        resourceNameOf(value),
        value.getVersion(),
        value.getVersionTag(),
        value.getTenantId(),
        value.getFormId(),
        value.getState() == null ? null : ProcessDefinitionState.valueOf(value.getState().name()));
  }

  /**
   * Documents written by the camunda/operate#5980 {@code CUSTOMCopyProcessesFromOptimize} hotfix
   * have no resource name, so it is derived from the BPMN process id.
   */
  private static String resourceNameOf(final ProcessEntity value) {
    final var resourceName = value.getResourceName();
    if (resourceName != null || value.getBpmnProcessId() == null) {
      return resourceName;
    }
    return value.getBpmnProcessId() + ".bpmn";
  }
}
