/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditLogEntity(
    Long auditLogKey,
    String entityKey,
    AuditLogEntityType entityType,
    AuditLogOperationType operationType,
    Long batchOperationKey,
    BatchOperationType batchOperationType,
    String timestamp,
    String actorId,
    AuditLogActorType actorType,
    String tenantId,
    AuditLogResult result,
    String annotation,
    AuditLogCategory category,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long elementInstanceKey,
    Long jobKey,
    Long userTaskKey,
    String decisionRequirementsId,
    Long decisionRequirementsKey,
    String decisionDefinitionId,
    Long decisionDefinitionKey,
    Long decisionEvaluationKey) {

  public enum AuditLogEntityType {
    AUTHORIZATION,
    BATCH,
    DECISION,
    GROUP,
    INCIDENT,
    MAPPING_RULE,
    PROCESS_INSTANCE,
    ROLE,
    TENANT,
    USER,
    USER_TASK,
    RESOURCE,
    VARIABLE
  }

  public enum AuditLogOperationType {
    ASSIGN,
    CANCEL,
    COMPLETE,
    CREATE,
    DELETE,
    EVALUATE,
    MIGRATE,
    MODIFY,
    RESOLVE,
    RESUME,
    SUSPEND,
    UNASSIGN,
    UPDATE
  }

  public enum AuditLogActorType {
    USER,
    CLIENT
  }

  public enum AuditLogResult {
    SUCCESS,
    FAIL
  }

  public enum AuditLogCategory {
    OPERATOR,
    USER_TASK,
    ADMIN
  }
}
