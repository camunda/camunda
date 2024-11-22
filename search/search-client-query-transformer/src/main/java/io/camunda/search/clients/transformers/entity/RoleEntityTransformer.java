/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static java.util.Optional.ofNullable;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.RoleEntity;
import java.util.Set;

public class RoleEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.usermanagement.RoleEntity, RoleEntity> {

  @Override
  public RoleEntity apply(
      final io.camunda.webapps.schema.entities.usermanagement.RoleEntity value) {
    return new RoleEntity(
        value.getKey(),
        value.getName(),
        ofNullable(value.getAssignedMemberKeys()).map(Set::copyOf).orElse(null));
  }
}
