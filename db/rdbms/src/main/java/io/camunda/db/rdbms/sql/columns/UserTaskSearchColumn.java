/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.UserTaskEntity;

public enum UserTaskSearchColumn implements SearchColumn<UserTaskEntity> {
  USER_TASK_KEY("userTaskKey"),
  CREATION_DATE("creationDate"),
  DUE_DATE("dueDate"),
  FOLLOW_UP_DATE("followUpDate"),
  COMPLETION_DATE("completionDate"),
  ELEMENT_ID("elementId"),
  NAME("name"),
  ELEMENT_INSTANCE_KEY("elementInstanceKey"),
  TENANT_ID("tenantId"),
  ASSIGNEE("assignee"),
  FORM_KEY("formKey"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_VERSION("processDefinitionVersion"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  PRIORITY("priority");

  private final String property;

  UserTaskSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<UserTaskEntity> getEntityClass() {
    return UserTaskEntity.class;
  }
}
