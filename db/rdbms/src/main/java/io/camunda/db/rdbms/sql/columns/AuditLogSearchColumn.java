/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.AuditLogEntity;

public enum AuditLogSearchColumn implements SearchColumn<AuditLogEntity> {
  AUDIT_LOG_KEY("auditLogKey"),
  ENTITY_KEY("entityKey"),
  ENTITY_TYPE("entityType"),
  OPERATION_TYPE("operationType"),
  BATCH_OPERATION_KEY("batchOperationKey"),
  BATCH_OPERATION_TYPE("batchOperationType"),
  TIMESTAMP("timestamp"),
  ACTOR_ID("actorId"),
  ACTOR_TYPE("actorType"),
  TENANT_ID("tenantId"),
  RESULT("result"),
  CATEGORY("category"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  ELEMENT_INSTANCE_KEY("elementInstanceKey"),
  JOB_KEY("jobKey"),
  USER_TASK_KEY("userTaskKey"),
  DECISION_REQUIREMENTS_ID("decisionRequirementsId"),
  DECISION_REQUIREMENTS_KEY("decisionRequirementsKey"),
  DECISION_DEFINITION_ID("decisionDefinitionId"),
  DECISION_DEFINITION_KEY("decisionDefinitionKey"),
  DECISION_EVALUATION_KEY("decisionEvaluationKey"),
  DEPLOYMENT_KEY("deploymentKey"),
  FORM_KEY("formKey"),
  RESOURCE_KEY("resourceKey");

  private final String property;

  AuditLogSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<AuditLogEntity> getEntityClass() {
    return AuditLogEntity.class;
  }
}
