/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.DecisionDefinitionEntity;
import java.util.function.Function;

public enum DecisionDefinitionSearchColumn implements SearchColumn<DecisionDefinitionEntity> {
  DECISION_DEFINITION_KEY("decisionDefinitionKey", DecisionDefinitionEntity::decisionDefinitionKey),
  DECISION_DEFINITION_ID("decisionDefinitionId", DecisionDefinitionEntity::decisionDefinitionId),
  NAME("name", DecisionDefinitionEntity::name),
  VERSION("version", DecisionDefinitionEntity::version),
  TENANT_ID("tenantId", DecisionDefinitionEntity::tenantId),
  DECISION_REQUIREMENTS_KEY(
      "decisionRequirementsKey", DecisionDefinitionEntity::decisionRequirementsKey),
  DECISION_REQUIREMENTS_ID(
      "decisionRequirementsId", DecisionDefinitionEntity::decisionRequirementsId);

  private final String property;
  private final Function<DecisionDefinitionEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  DecisionDefinitionSearchColumn(
      final String property, final Function<DecisionDefinitionEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  DecisionDefinitionSearchColumn(
      final String property,
      final Function<DecisionDefinitionEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final DecisionDefinitionEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static DecisionDefinitionSearchColumn findByProperty(final String property) {
    for (final DecisionDefinitionSearchColumn column : DecisionDefinitionSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
