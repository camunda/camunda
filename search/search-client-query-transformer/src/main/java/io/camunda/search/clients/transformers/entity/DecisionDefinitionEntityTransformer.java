/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.DecisionDefinitionEntity;

public class DecisionDefinitionEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity,
        DecisionDefinitionEntity> {

  @Override
  public DecisionDefinitionEntity apply(
      final io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity source) {
    return new DecisionDefinitionEntity(
        source.getKey(),
        source.getDecisionId(),
        source.getName(),
        source.getVersion(),
        source.getDecisionRequirementsId(),
        source.getDecisionRequirementsKey(),
        source.getTenantId());
  }
}
