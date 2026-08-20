/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.RoleEntity;

public enum RoleSearchColumn implements SearchColumn<RoleEntity> {
  ROLE_KEY("roleKey"),
  ROLE_ID("roleId"),
  NAME("name"),
  DESCRIPTION("description");

  private final String property;

  RoleSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<RoleEntity> getEntityClass() {
    return RoleEntity.class;
  }
}
