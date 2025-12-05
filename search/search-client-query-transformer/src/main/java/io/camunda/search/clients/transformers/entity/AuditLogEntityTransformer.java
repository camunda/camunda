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
        value.getEntityType(),
        value.getOperationType(),
        value.getBatchOperationKey(),
        mapBatchOperationType(value.getBatchOperationType()),
        value.getTimestamp(),
        value.getActorId(),
        value.getActorType(),
        value.getTenantId(),
        value.getResult(),
        value.getAnnotation(),
        value.getCategory(),
        value.getProcessDefinitionId(),
        value.getProcessDefinitionKey(),
        value.getProcessInstanceKey(),
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
        value.getResourceKey());
  }

  private BatchOperationType mapBatchOperationType(
      final io.camunda.zeebe.protocol.record.value.BatchOperationType batchOperationType) {
    if (batchOperationType == null) {
      return null;
    }

    return switch (batchOperationType) {
      case RESOLVE_INCIDENT -> BatchOperationType.RESOLVE_INCIDENT;
      case CANCEL_PROCESS_INSTANCE -> BatchOperationType.CANCEL_PROCESS_INSTANCE;
      case DELETE_PROCESS_INSTANCE -> BatchOperationType.DELETE_PROCESS_INSTANCE;
      case MODIFY_PROCESS_INSTANCE -> BatchOperationType.MODIFY_PROCESS_INSTANCE;
      case MIGRATE_PROCESS_INSTANCE -> BatchOperationType.MIGRATE_PROCESS_INSTANCE;
    };
  }
}
