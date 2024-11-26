/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.FormEntity;
import java.util.function.Function;

public enum FormSearchColumn implements SearchColumn<FormEntity> {
  VERSION("version", FormEntity::version),
  FORM_KEY("formKey", FormEntity::formKey);

  private final String property;
  private final Function<FormEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  FormSearchColumn(final String property, final Function<FormEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  FormSearchColumn(
      final String property,
      final Function<FormEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final FormEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static FormSearchColumn findByProperty(final String property) {
    for (final FormSearchColumn column : FormSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
