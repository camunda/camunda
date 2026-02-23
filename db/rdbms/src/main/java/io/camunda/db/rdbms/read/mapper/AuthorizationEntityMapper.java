/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.search.entities.AuthorizationEntity;
import java.util.HashSet;

public class AuthorizationEntityMapper {

  public static AuthorizationEntity toEntity(final AuthorizationDbModel model) {
    return new AuthorizationEntity(
        model.authorizationKey(),
        model.ownerId(),
        model.ownerType(),
        model.resourceType(),
        model.resourceMatcher(),
        model.resourceId(),
        model.resourcePropertyName(),
        model.permissionTypes() != null ? new HashSet<>(model.permissionTypes()) : null);
  }
}
