/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.BatchOperationEntity;
import java.util.function.Function;

public enum BatchOperationSearchColumn implements SearchColumn<BatchOperationEntity> {
  BATCH_OPERATION_KEY("batchOperationKey", BatchOperationEntity::batchOperationKey);

  private final String property;
  private final Function<BatchOperationEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  BatchOperationSearchColumn(
      final String property, final Function<BatchOperationEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  BatchOperationSearchColumn(
      final String property,
      final Function<BatchOperationEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final BatchOperationEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static BatchOperationSearchColumn findByProperty(final String property) {
    for (final BatchOperationSearchColumn column : BatchOperationSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
