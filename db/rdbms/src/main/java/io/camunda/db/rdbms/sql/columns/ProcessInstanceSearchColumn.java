/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.zeebe.util.DateUtil;
import java.util.function.Function;

public enum ProcessInstanceSearchColumn implements SearchColumn<ProcessInstanceEntity> {
  PROCESS_INSTANCE_KEY("processInstanceKey", ProcessInstanceEntity::processInstanceKey),
  PROCESS_DEFINITION_KEY("processDefinitionKey", ProcessInstanceEntity::processDefinitionKey),
  PROCESS_DEFINITION_ID("processDefinitionId", ProcessInstanceEntity::processDefinitionId),
  PROCESS_DEFINITION_NAME("processDefinitionName", ProcessInstanceEntity::processDefinitionName),
  PROCESS_DEFINITION_VERSION(
      "processDefinitionVersion", ProcessInstanceEntity::processDefinitionVersion),
  PROCESS_DEFINITION_VERSION_TAG(
      "processDefinitionVersionTag", ProcessInstanceEntity::processDefinitionVersionTag),
  START_DATE("startDate", ProcessInstanceEntity::startDate, DateUtil::fuzzyToOffsetDateTime),
  END_DATE("endDate", ProcessInstanceEntity::endDate, DateUtil::fuzzyToOffsetDateTime),
  STATE("state", ProcessInstanceEntity::state),
  TENANT_ID("tenantId", ProcessInstanceEntity::tenantId),
  PARENT_PROCESS_INSTANCE_KEY(
      "parentProcessInstanceKey", ProcessInstanceEntity::parentProcessInstanceKey),
  PARENT_ELEMENT_INSTANCE_KEY(
      "parentFlowNodeInstanceKey", ProcessInstanceEntity::parentFlowNodeInstanceKey),
  HAS_INCIDENT("hasIncident", ProcessInstanceEntity::hasIncident);

  private final String property;
  private final Function<ProcessInstanceEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  ProcessInstanceSearchColumn(
      final String property, final Function<ProcessInstanceEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  ProcessInstanceSearchColumn(
      final String property,
      final Function<ProcessInstanceEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final ProcessInstanceEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static ProcessInstanceSearchColumn findByProperty(final String property) {
    for (final ProcessInstanceSearchColumn column : ProcessInstanceSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
