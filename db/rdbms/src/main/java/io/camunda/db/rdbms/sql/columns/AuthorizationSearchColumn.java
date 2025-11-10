/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.AuthorizationEntity;

public enum AuthorizationSearchColumn implements SearchColumn<AuthorizationEntity> {
  OWNER_ID("ownerId"),
  OWNER_TYPE("ownerType"),
  RESOURCE_TYPE("resourceType"),
  RESOURCE_ID("resourceId"),
  RESOURCE_PROPERTY_NAME("resourcePropertyName");

  private final String property;

  AuthorizationSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<AuthorizationEntity> getEntityClass() {
    return AuthorizationEntity.class;
  }
}
