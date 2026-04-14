/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.ResourceEntity;

public class ResourceEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.resource.ResourceEntity, ResourceEntity> {

  @Override
  public ResourceEntity apply(
      final io.camunda.webapps.schema.entities.resource.ResourceEntity value) {
    return new ResourceEntity(
        value.getResourceKey(),
        value.getResourceId(),
        value.getResourceName(),
        value.getVersion(),
        value.getVersionTag(),
        value.getDeploymentKey(),
        value.getTenantId());
  }
}
