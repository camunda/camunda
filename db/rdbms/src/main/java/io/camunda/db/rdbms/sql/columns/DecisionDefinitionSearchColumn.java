/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.DecisionDefinitionEntity;

public enum DecisionDefinitionSearchColumn implements SearchColumn<DecisionDefinitionEntity> {
  DECISION_DEFINITION_KEY("decisionDefinitionKey"),
  DECISION_DEFINITION_ID("decisionDefinitionId"),
  NAME("name"),
  VERSION("version"),
  TENANT_ID("tenantId"),
  DECISION_REQUIREMENTS_KEY("decisionRequirementsKey"),
  DECISION_REQUIREMENTS_ID("decisionRequirementsId"),
  DECISION_REQUIREMENTS_NAME("decisionRequirementsName"),
  DECISION_REQUIREMENTS_VERSION("decisionRequirementsVersion");

  private final String property;

  DecisionDefinitionSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<DecisionDefinitionEntity> getEntityClass() {
    return DecisionDefinitionEntity.class;
  }
}
