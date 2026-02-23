/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.search.entities.AuditLogEntity;

public class AuditLogEntityMapper {

  public static AuditLogEntity toEntity(final AuditLogDbModel auditLogDbModel) {
    return new AuditLogEntity.Builder()
        .auditLogKey(auditLogDbModel.auditLogKey())
        .entityKey(auditLogDbModel.entityKey())
        .entityType(auditLogDbModel.entityType())
        .operationType(auditLogDbModel.operationType())
        .batchOperationKey(auditLogDbModel.batchOperationKey())
        .batchOperationType(auditLogDbModel.batchOperationType())
        .timestamp(auditLogDbModel.timestamp())
        .actorType(auditLogDbModel.actorType())
        .actorId(auditLogDbModel.actorId())
        .agentElementId(auditLogDbModel.agentElementId())
        .tenantId(auditLogDbModel.tenantId())
        .tenantScope(auditLogDbModel.tenantScope())
        .result(auditLogDbModel.result())
        .annotation(auditLogDbModel.annotation())
        .category(auditLogDbModel.category())
        .processDefinitionId(auditLogDbModel.processDefinitionId())
        .decisionRequirementsId(auditLogDbModel.decisionRequirementsId())
        .decisionDefinitionId(auditLogDbModel.decisionDefinitionId())
        .processDefinitionKey(auditLogDbModel.processDefinitionKey())
        .processInstanceKey(auditLogDbModel.processInstanceKey())
        .rootProcessInstanceKey(auditLogDbModel.rootProcessInstanceKey())
        .elementInstanceKey(auditLogDbModel.elementInstanceKey())
        .jobKey(auditLogDbModel.jobKey())
        .userTaskKey(auditLogDbModel.userTaskKey())
        .decisionRequirementsKey(auditLogDbModel.decisionRequirementsKey())
        .decisionDefinitionKey(auditLogDbModel.decisionDefinitionKey())
        .decisionEvaluationKey(auditLogDbModel.decisionEvaluationKey())
        .deploymentKey(auditLogDbModel.deploymentKey())
        .formKey(auditLogDbModel.formKey())
        .resourceKey(auditLogDbModel.resourceKey())
        .relatedEntityType(auditLogDbModel.relatedEntityType())
        .relatedEntityKey(auditLogDbModel.relatedEntityKey())
        .entityDescription(auditLogDbModel.entityDescription())
        .build();
  }
}
