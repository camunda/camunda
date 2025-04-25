/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.GroupEntity;
import java.util.function.Function;

public enum GroupSearchColumn implements SearchColumn<GroupEntity> {
  GROUP_KEY("groupKey", GroupEntity::groupKey),
  GROUP_ID("groupId", GroupEntity::groupId),
  NAME("name", GroupEntity::name),
  DESCRIPTION("description", GroupEntity::description);

  private final String property;
  private final Function<GroupEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  GroupSearchColumn(final String property, final Function<GroupEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  GroupSearchColumn(
      final String property,
      final Function<GroupEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final GroupEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static GroupSearchColumn findByProperty(final String property) {
    for (final GroupSearchColumn column : GroupSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
