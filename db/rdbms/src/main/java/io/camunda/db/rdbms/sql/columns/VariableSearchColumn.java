/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.VariableEntity;
import java.util.function.Function;

public enum VariableSearchColumn implements SearchColumn<VariableEntity> {
  VAR_KEY("variableKey", VariableEntity::variableKey),
  PROCESS_INSTANCE_KEY("processInstanceKey", VariableEntity::processInstanceKey),
  SCOPE_KEY("scopeKey", VariableEntity::scopeKey),
  VAR_NAME("name", VariableEntity::name),
  VAR_VALUE("value", VariableEntity::value),
  VAR_FULL_VALUE("fullValue", VariableEntity::fullValue),
  TENANT_ID("tenantId", VariableEntity::tenantId),
  IS_PREVIEW("isPreview", VariableEntity::isPreview),
  PROCESS_DEFINITION_ID("processDefinitionId", VariableEntity::processDefinitionId);

  private final String property;
  private final Function<VariableEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  VariableSearchColumn(
      final String property, final Function<VariableEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  VariableSearchColumn(
      final String property,
      final Function<VariableEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final VariableEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static VariableSearchColumn findByProperty(final String property) {
    for (final VariableSearchColumn column : VariableSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }
    return null;
  }
}
