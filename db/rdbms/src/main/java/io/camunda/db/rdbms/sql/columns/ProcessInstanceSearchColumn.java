/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.ProcessInstanceEntity;

public enum ProcessInstanceSearchColumn implements SearchColumn<ProcessInstanceEntity> {
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_NAME("processDefinitionName"),
  PROCESS_DEFINITION_VERSION("processDefinitionVersion"),
  PROCESS_DEFINITION_VERSION_TAG("processDefinitionVersionTag"),
  START_DATE("startDate"),
  END_DATE("endDate"),
  STATE("state"),
  TENANT_ID("tenantId"),
  PARENT_PROCESS_INSTANCE_KEY("parentProcessInstanceKey"),
  PARENT_ELEMENT_INSTANCE_KEY("parentFlowNodeInstanceKey"),
  HAS_INCIDENT("hasIncident");

  private final String property;

  ProcessInstanceSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<ProcessInstanceEntity> getEntityClass() {
    return ProcessInstanceEntity.class;
  }
}
