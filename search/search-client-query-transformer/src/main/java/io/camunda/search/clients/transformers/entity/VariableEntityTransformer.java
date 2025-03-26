/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.VariableEntity;

public class VariableEntityTransformer
    implements ServiceTransformer<
    io.camunda.webapps.schema.entities.VariableEntity, VariableEntity> {

  @Override
  public VariableEntity apply(
      final io.camunda.webapps.schema.entities.VariableEntity source) {
    return new VariableEntity(
        source.getKey(),
        source.getName(),
        source.getValue(),
        source.getFullValue(),
        source.getIsPreview(),
        source.getScopeKey(),
        source.getProcessInstanceKey(),
        source.getBpmnProcessId(),
        source.getTenantId());
  }
}
