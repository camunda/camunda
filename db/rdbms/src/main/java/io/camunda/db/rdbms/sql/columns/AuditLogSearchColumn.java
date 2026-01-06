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
  TIMESTAMP("timestamp"),
  ACTOR_TYPE("actorType"),
  ACTOR_ID("actorId"),
  TENANT_ID("tenantId"),
  RESULT("result"),
  CATEGORY("category"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  USER_TASK_KEY("userTaskKey"),
  DECISION_DEFINITION_KEY("decisionDefinitionKey"),
  DECISION_EVALUATION_KEY("decisionEvaluationKey"),
  JOB_KEY("jobKey"),
  BATCH_OPERATION_KEY("batchOperationKey"),
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
