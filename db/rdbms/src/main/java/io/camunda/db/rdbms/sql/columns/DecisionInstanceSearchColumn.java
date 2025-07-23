/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.DecisionInstanceEntity;

public enum DecisionInstanceSearchColumn implements SearchColumn<DecisionInstanceEntity> {
  DECISION_INSTANCE_ID("decisionInstanceId"),
  DECISION_INSTANCE_KEY("decisionInstanceKey"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  FLOW_NODE_INSTANCE_KEY("flowNodeInstanceKey"),
  DECISION_DEFINITION_NAME("decisionDefinitionName"),
  DECISION_DEFINITION_ID("decisionDefinitionId"),
  DECISION_DEFINITION_KEY("decisionDefinitionKey"),
  DECISION_DEFINITION_VERSION("decisionDefinitionVersion"),
  DECISION_DEFINITION_TYPE("decisionDefinitionType"),
  TENANT_ID("tenantId"),
  EVALUATION_DATE("evaluationDate"),
  STATE("state"),
  RESULT("result"),
  EVALUATION_FAILURE("evaluationFailure"),
  EVALUATION_FAILURE_MESSAGE("evaluationFailureMessage");
  private final String property;

  DecisionInstanceSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<DecisionInstanceEntity> getEntityClass() {
    return DecisionInstanceEntity.class;
  }
}
