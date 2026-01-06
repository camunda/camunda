/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.ClusterVariableScope;

public class ClusterVariableEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity,
        ClusterVariableEntity> {

  @Override
  public ClusterVariableEntity apply(
      final io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity value) {
    return new ClusterVariableEntity(
        value.getId(),
        value.getName(),
        value.getValue(),
        value.getFullValue(),
        value.getIsPreview(),
        ClusterVariableScope.valueOf(value.getScope().name()),
        value.getTenantId());
  }
}
