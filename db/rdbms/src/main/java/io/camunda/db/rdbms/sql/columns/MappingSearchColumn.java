/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.MappingEntity;
import java.util.function.Function;

public enum MappingSearchColumn implements SearchColumn<MappingEntity> {
  MAPPING_ID("mappingId", MappingEntity::mappingId),
  MAPPING_KEY("mappingKey", MappingEntity::mappingKey),
  CLAIM_NAME("claimName", MappingEntity::claimName),
  CLAIM_VALUE("claimValue", MappingEntity::claimValue),
  NAME("name", MappingEntity::name);

  private final String property;
  private final Function<MappingEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  MappingSearchColumn(final String property, final Function<MappingEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  MappingSearchColumn(
      final String property,
      final Function<MappingEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final MappingEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static MappingSearchColumn findByProperty(final String property) {
    for (final MappingSearchColumn column : MappingSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }
    return null;
  }
}
