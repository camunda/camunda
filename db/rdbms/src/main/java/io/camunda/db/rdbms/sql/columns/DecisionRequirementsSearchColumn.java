/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.DecisionRequirementsEntity;

public enum DecisionRequirementsSearchColumn implements SearchColumn<DecisionRequirementsEntity> {
  DECISION_REQUIREMENTS_KEY("decisionRequirementsKey"),
  DECISION_REQUIREMENTS_ID("decisionRequirementsId"),
  NAME("name"),
  VERSION("version"),
  TENANT_ID("tenantId"),
  RESOURCE_NAME("resourceName"),
  XML("xml");

  private final String property;

  DecisionRequirementsSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<DecisionRequirementsEntity> getEntityClass() {
    return DecisionRequirementsEntity.class;
  }
}
