/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.UserEntity;

public enum UserSearchColumn implements SearchColumn<UserEntity> {
  USER_KEY("userKey"),
  USERNAME("username"),
  NAME("name"),
  EMAIL("email");

  private final String property;

  UserSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<UserEntity> getEntityClass() {
    return UserEntity.class;
  }
}
