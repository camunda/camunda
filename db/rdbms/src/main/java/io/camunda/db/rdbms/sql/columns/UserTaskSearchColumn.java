/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.UserTaskEntity;
import java.util.function.Function;

public enum UserTaskSearchColumn implements SearchColumn<UserTaskEntity> {
  USER_TASK_KEY("userKey", UserTaskEntity::userTaskKey),
  CREATION_DATE("creationDate", UserTaskEntity::creationDate),
  DUE_DATE("dueDate", UserTaskEntity::dueDate),
  FOLLOW_UP_DATE("followUpDate", UserTaskEntity::followUpDate),
  COMPLETION_DATE("completionDate", UserTaskEntity::completionDate),
  ELEMENT_ID("elementId", UserTaskEntity::elementId),
  ELEMENT_INSTANCE_KEY("elementInstanceKey", UserTaskEntity::elementInstanceKey),
  TENANT_ID("tenantId", UserTaskEntity::tenantId),
  ASSIGNEE("assignee", UserTaskEntity::assignee),
  FORM_KEY("formKey", UserTaskEntity::formKey),
  PROCESS_DEFINITION_ID("processDefinitionId", UserTaskEntity::processDefinitionId),
  PROCESS_DEFINITION_KEY("processDefinitionKey", UserTaskEntity::processDefinitionKey),
  PROCESS_DEFINITION_VERSION("processDefinitionVersion", UserTaskEntity::processDefinitionVersion),
  PROCESS_INSTANCE_KEY("processInstanceKey", UserTaskEntity::processInstanceKey),
  PRIORITY("priority", UserTaskEntity::priority);

  private final String property;
  private final Function<UserTaskEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  UserTaskSearchColumn(
      final String property, final Function<UserTaskEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  UserTaskSearchColumn(
      final String property,
      final Function<UserTaskEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final UserTaskEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static UserTaskSearchColumn findByProperty(final String property) {
    for (final UserTaskSearchColumn column : UserTaskSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
