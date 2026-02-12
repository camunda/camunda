/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.BatchOperationType;

public class AuditLogEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.auditlog.AuditLogEntity, AuditLogEntity> {

  @Override
  public AuditLogEntity apply(
      final io.camunda.webapps.schema.entities.auditlog.AuditLogEntity value) {
    return new AuditLogEntity(
        value.getId(),
        value.getEntityKey(),
        mapEntityType(value.getEntityType()),
        mapOperationType(value.getOperationType()),
        value.getBatchOperationKey(),
        mapBatchOperationType(value.getBatchOperationType()),
        value.getTimestamp(),
        value.getActorId(),
        mapActorType(value.getActorType()),
        value.getAgentElementId(),
        value.getTenantId(),
        mapTenantScope(value.getTenantScope()),
        mapOperationResult(value.getResult()),
        value.getAnnotation(),
        mapOperationCategory(value.getCategory()),
        value.getProcessDefinitionId(),
        value.getProcessDefinitionKey(),
        value.getProcessInstanceKey(),
        value.getRootProcessInstanceKey(),
        value.getElementInstanceKey(),
        value.getJobKey(),
        value.getUserTaskKey(),
        value.getDecisionRequirementsId(),
        value.getDecisionRequirementsKey(),
        value.getDecisionDefinitionId(),
        value.getDecisionDefinitionKey(),
        value.getDecisionEvaluationKey(),
        value.getDeploymentKey(),
        value.getFormKey(),
        value.getResourceKey(),
        mapEntityType(value.getRelatedEntityType()),
        value.getRelatedEntityKey(),
        value.getEntityDescription());
  }

  private AuditLogEntity.AuditLogEntityType mapEntityType(
      final io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType entityType) {
    return entityType != null ? AuditLogEntity.AuditLogEntityType.valueOf(entityType.name()) : null;
  }

  private AuditLogEntity.AuditLogOperationType mapOperationType(
      final io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType operationType) {
    return operationType != null
        ? AuditLogEntity.AuditLogOperationType.valueOf(operationType.name())
        : null;
  }

  private BatchOperationType mapBatchOperationType(
      final io.camunda.zeebe.protocol.record.value.BatchOperationType batchOperationType) {
    return batchOperationType != null
        ? BatchOperationType.valueOf(batchOperationType.name())
        : null;
  }

  private AuditLogEntity.AuditLogActorType mapActorType(
      final io.camunda.webapps.schema.entities.auditlog.AuditLogActorType actorType) {
    return actorType != null ? AuditLogEntity.AuditLogActorType.valueOf(actorType.name()) : null;
  }

  private AuditLogEntity.AuditLogTenantScope mapTenantScope(
      final io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope result) {
    return result != null ? AuditLogEntity.AuditLogTenantScope.valueOf(result.name()) : null;
  }

  private AuditLogEntity.AuditLogOperationResult mapOperationResult(
      final io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult result) {
    return result != null ? AuditLogEntity.AuditLogOperationResult.valueOf(result.name()) : null;
  }

  private AuditLogEntity.AuditLogOperationCategory mapOperationCategory(
      final io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory category) {
    return category != null
        ? AuditLogEntity.AuditLogOperationCategory.valueOf(category.name())
        : null;
  }
}
