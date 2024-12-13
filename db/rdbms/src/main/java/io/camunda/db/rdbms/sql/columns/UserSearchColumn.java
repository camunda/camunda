/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.UserEntity;
import java.util.function.Function;

public enum UserSearchColumn implements SearchColumn<UserEntity> {
  USER_KEY("userKey", UserEntity::userKey),
  USERNAME("username", UserEntity::username),
  NAME("name", UserEntity::name),
  EMAIL("email", UserEntity::email);

  private final String property;
  private final Function<UserEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  UserSearchColumn(final String property, final Function<UserEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  UserSearchColumn(
      final String property,
      final Function<UserEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final UserEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static UserSearchColumn findByProperty(final String property) {
    for (final UserSearchColumn column : UserSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
