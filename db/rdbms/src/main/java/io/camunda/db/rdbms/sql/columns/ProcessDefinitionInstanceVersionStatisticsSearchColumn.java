/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;

public enum ProcessDefinitionInstanceVersionStatisticsSearchColumn
    implements SearchColumn<ProcessDefinitionInstanceVersionStatisticsEntity> {
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_VERSION("processDefinitionVersion"),
  PROCESS_DEFINITION_NAME("processDefinitionName"),
  ACTIVE_INSTANCES_WITHOUT_INCIDENT_COUNT("activeInstancesWithoutIncidentCount"),
  ACTIVE_INSTANCES_WITH_INCIDENT_COUNT("activeInstancesWithIncidentCount");

  private final String property;

  ProcessDefinitionInstanceVersionStatisticsSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<ProcessDefinitionInstanceVersionStatisticsEntity> getEntityClass() {
    return ProcessDefinitionInstanceVersionStatisticsEntity.class;
  }
}
