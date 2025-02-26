/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.TenantEntity;
import java.util.function.Function;

public enum TenantSearchColumn implements SearchColumn<TenantEntity> {
  TENANT_ID("tenantId", TenantEntity::tenantId),
  NAME("name", TenantEntity::name);

  private final String property;
  private final Function<TenantEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  TenantSearchColumn(final String property, final Function<TenantEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  TenantSearchColumn(
      final String property,
      final Function<TenantEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final TenantEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static TenantSearchColumn findByProperty(final String property) {
    for (final TenantSearchColumn column : TenantSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }
    return null;
  }
}
