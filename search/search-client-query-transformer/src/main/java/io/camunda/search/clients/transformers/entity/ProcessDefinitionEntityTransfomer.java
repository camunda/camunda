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
        value.getResourceName(),
        value.getVersion(),
        value.getVersionTag(),
        value.getTenantId(),
        value.getFormId());
  }
}
