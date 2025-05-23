/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import java.util.function.Function;

public enum BatchOperationItemSearchColumn implements SearchColumn<BatchOperationItemEntity> {
  BATCH_OPERATION_ID("batchOperationId", BatchOperationItemEntity::batchOperationId),
  ITEM_KEY("itemKey", BatchOperationItemEntity::itemKey),
  PROCESS_INSTANCE_KEY("processInstanceKey", BatchOperationItemEntity::processInstanceKey),
  STATE("state", BatchOperationItemEntity::state),
  PROCESSED_DATE("processedDate", BatchOperationItemEntity::processedDate);

  private final String property;
  private final Function<BatchOperationItemEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  BatchOperationItemSearchColumn(
      final String property, final Function<BatchOperationItemEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  BatchOperationItemSearchColumn(
      final String property,
      final Function<BatchOperationItemEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final BatchOperationItemEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static BatchOperationItemSearchColumn findByProperty(final String property) {
    for (final BatchOperationItemSearchColumn column : BatchOperationItemSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
