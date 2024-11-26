/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.DecisionRequirementsEntity;
import java.util.function.Function;

public enum DecisionRequirementsSearchColumn implements SearchColumn<DecisionRequirementsEntity> {
  DECISION_REQUIREMENTS_KEY(
      "decisionRequirementsKey", DecisionRequirementsEntity::decisionRequirementsKey),
  DECISION_REQUIREMENTS_ID(
      "decisionRequirementsId", DecisionRequirementsEntity::decisionRequirementsId),
  NAME("name", DecisionRequirementsEntity::name),
  VERSION("version", DecisionRequirementsEntity::version),
  TENANT_ID("tenantId", DecisionRequirementsEntity::tenantId),
  RESOURCE_NAME("resourceName", DecisionRequirementsEntity::resourceName),
  XML("xml", DecisionRequirementsEntity::xml);

  private final String property;
  private final Function<DecisionRequirementsEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  DecisionRequirementsSearchColumn(
      final String property, final Function<DecisionRequirementsEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  DecisionRequirementsSearchColumn(
      final String property,
      final Function<DecisionRequirementsEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final DecisionRequirementsEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static DecisionRequirementsSearchColumn findByProperty(final String property) {
    for (final DecisionRequirementsSearchColumn column :
        DecisionRequirementsSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
