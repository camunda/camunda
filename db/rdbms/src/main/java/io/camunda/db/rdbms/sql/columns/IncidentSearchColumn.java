/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.IncidentEntity;

public enum IncidentSearchColumn implements SearchColumn<IncidentEntity> {
  INCIDENT_KEY("incidentKey"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  FLOW_NODE_INSTANCE_KEY("flowNodeInstanceKey"),
  FLOW_NODE_ID("flowNodeId"),
  CREATION_DATE("creationTime"),
  ERROR_TYPE("errorType"),
  ERROR_MESSAGE("errorMessage"),
  STATE("state"),
  JOB_KEY("jobKey"),
  TENANT_ID("tenantId");

  private final String property;

  IncidentSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<IncidentEntity> getEntityClass() {
    return IncidentEntity.class;
  }
}
