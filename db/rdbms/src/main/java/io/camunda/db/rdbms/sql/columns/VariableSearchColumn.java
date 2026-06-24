/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.VariableEntity;

public enum VariableSearchColumn implements SearchColumn<VariableEntity> {
  VAR_KEY("variableKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  SCOPE_KEY("scopeKey"),
  VAR_NAME("name"),
  VAR_VALUE("value"),
  VAR_FULL_VALUE("fullValue"),
  TENANT_ID("tenantId"),
  IS_PREVIEW("isPreview"),
  PROCESS_DEFINITION_ID("processDefinitionId");

  private final String property;

  VariableSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<VariableEntity> getEntityClass() {
    return VariableEntity.class;
  }
}
