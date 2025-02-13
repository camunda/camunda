/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.AuthorizationEntity;
import java.util.function.Function;

public enum AuthorizationSearchColumn implements SearchColumn<AuthorizationEntity> {
  OWNER_KEY("ownerKey", AuthorizationEntity::ownerId),
  OWNER_TYPE("ownerType", AuthorizationEntity::ownerType),
  RESOURCE_TYPE("resourceType", AuthorizationEntity::resourceType);

  private final String property;
  private final Function<AuthorizationEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  AuthorizationSearchColumn(
      final String property, final Function<AuthorizationEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  AuthorizationSearchColumn(
      final String property,
      final Function<AuthorizationEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final AuthorizationEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static AuthorizationSearchColumn findByProperty(final String property) {
    for (final AuthorizationSearchColumn column : AuthorizationSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
