/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.FlowNodeInstanceEntity;

public enum FlowNodeInstanceSearchColumn implements SearchColumn<FlowNodeInstanceEntity> {
  FLOW_NODE_INSTANCE_KEY("flowNodeInstanceKey"),
  FLOW_NODE_ID("flowNodeId"),
  FLOW_NODE_NAME("flowNodeName"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  START_DATE("startDate"),
  END_DATE("endDate"),
  STATE("state"),
  TYPE("type"),
  TENANT_ID("tenantId"),
  TREE_PATH("treePath"),
  INCIDENT_KEY("incidentKey"),
  INCIDENT("hasIncident");

  private final String property;

  FlowNodeInstanceSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<FlowNodeInstanceEntity> getEntityClass() {
    return FlowNodeInstanceEntity.class;
  }
}
