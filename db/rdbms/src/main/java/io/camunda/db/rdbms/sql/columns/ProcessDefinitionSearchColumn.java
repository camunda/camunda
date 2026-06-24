/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.ProcessDefinitionEntity;

public enum ProcessDefinitionSearchColumn implements SearchColumn<ProcessDefinitionEntity> {
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  NAME("name"),
  VERSION("version"),
  VERSION_TAG("versionTag"),
  TENANT_ID("tenantId"),
  FORM_ID("formId"),
  RESOURCE_NAME("resourceName"),
  BPMN_XML("bpmnXml");

  private final String property;

  ProcessDefinitionSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<ProcessDefinitionEntity> getEntityClass() {
    return ProcessDefinitionEntity.class;
  }
}
