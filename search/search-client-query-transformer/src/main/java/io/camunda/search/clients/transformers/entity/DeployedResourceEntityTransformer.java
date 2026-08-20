/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.DeployedResourceEntity;

public class DeployedResourceEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.resource.DeployedResourceEntity,
        DeployedResourceEntity> {

  @Override
  public DeployedResourceEntity apply(
      final io.camunda.webapps.schema.entities.resource.DeployedResourceEntity value) {
    return new DeployedResourceEntity(
        value.getResourceKey(),
        value.getResourceId(),
        value.getResourceName(),
        value.getResourceType(),
        value.getVersion(),
        value.getVersionTag(),
        value.getDeploymentKey(),
        value.getTenantId(),
        value.getResourceContent());
  }
}
