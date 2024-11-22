/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.zeebe.util.DateUtil;
import java.util.function.Function;

public enum DecisionInstanceSearchColumn implements SearchColumn<DecisionInstanceEntity> {
  DECISION_INSTANCE_ID("decisionInstanceId", DecisionInstanceEntity::decisionInstanceId),
  DECISION_INSTANCE_KEY("decisionInstanceKey", DecisionInstanceEntity::decisionInstanceKey),
  PROCESS_DEFINITION_KEY("processDefinitionKey", DecisionInstanceEntity::processDefinitionKey),
  DECISION_DEFINITION_NAME(
      "decisionDefinitionName", DecisionInstanceEntity::decisionDefinitionName),
  DECISION_DEFINITION_ID("decisionDefinitionId", DecisionInstanceEntity::decisionDefinitionId),
  DECISION_DEFINITION_KEY("decisionDefinitionKey", DecisionInstanceEntity::decisionDefinitionKey),
  DECISION_DEFINITION_VERSION(
      "decisionDefinitionVersion", DecisionInstanceEntity::decisionDefinitionVersion),
  DECISION_DEFINITION_TYPE(
      "decisionDefinitionType", DecisionInstanceEntity::decisionDefinitionType),
  TENANT_ID("tenantId", DecisionInstanceEntity::tenantId),
  EVALUATION_DATE(
      "evaluationDate", DecisionInstanceEntity::evaluationDate, DateUtil::fuzzyToOffsetDateTime),
  STATE("state", DecisionInstanceEntity::state),
  RESULT("result", DecisionInstanceEntity::result),
  EVALUATION_FAILURE("evaluationFailure", DecisionInstanceEntity::evaluationFailure);

  private final String property;
  private final Function<DecisionInstanceEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  DecisionInstanceSearchColumn(
      final String property, final Function<DecisionInstanceEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  DecisionInstanceSearchColumn(
      final String property,
      final Function<DecisionInstanceEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final DecisionInstanceEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static DecisionInstanceSearchColumn findByProperty(final String property) {
    for (final DecisionInstanceSearchColumn column : DecisionInstanceSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
