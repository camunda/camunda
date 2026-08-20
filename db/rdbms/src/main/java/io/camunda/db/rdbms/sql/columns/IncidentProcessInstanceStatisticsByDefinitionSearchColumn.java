/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;

public enum IncidentProcessInstanceStatisticsByDefinitionSearchColumn
    implements SearchColumn<IncidentProcessInstanceStatisticsByDefinitionEntity> {
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_NAME("processDefinitionName"),
  PROCESS_DEFINITION_VERSION("processDefinitionVersion"),
  TENANT_ID("tenantId"),
  ACTIVE_INSTANCES_WITH_ERROR_COUNT("activeInstancesWithErrorCount");

  private final String property;

  IncidentProcessInstanceStatisticsByDefinitionSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<IncidentProcessInstanceStatisticsByDefinitionEntity> getEntityClass() {
    return IncidentProcessInstanceStatisticsByDefinitionEntity.class;
  }
}
