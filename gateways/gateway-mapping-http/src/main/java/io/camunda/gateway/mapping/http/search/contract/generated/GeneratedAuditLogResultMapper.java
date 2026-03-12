/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.AuditLogResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedAuditLogResultMapper {

  private GeneratedAuditLogResultMapper() {}

  public static AuditLogResult toProtocol(final GeneratedAuditLogStrictContract source) {
    return new AuditLogResult()
        .auditLogKey(source.auditLogKey())
        .entityKey(source.entityKey())
        .entityType(source.entityType())
        .operationType(source.operationType())
        .batchOperationKey(source.batchOperationKey())
        .batchOperationType(source.batchOperationType())
        .timestamp(source.timestamp())
        .actorId(source.actorId())
        .actorType(source.actorType())
        .agentElementId(source.agentElementId())
        .tenantId(source.tenantId())
        .result(source.result())
        .annotation(source.annotation())
        .category(source.category())
        .processDefinitionId(source.processDefinitionId())
        .processDefinitionKey(source.processDefinitionKey())
        .processInstanceKey(source.processInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .elementInstanceKey(source.elementInstanceKey())
        .jobKey(source.jobKey())
        .userTaskKey(source.userTaskKey())
        .decisionRequirementsId(source.decisionRequirementsId())
        .decisionRequirementsKey(source.decisionRequirementsKey())
        .decisionDefinitionId(source.decisionDefinitionId())
        .decisionDefinitionKey(source.decisionDefinitionKey())
        .decisionEvaluationKey(source.decisionEvaluationKey())
        .deploymentKey(source.deploymentKey())
        .formKey(source.formKey())
        .resourceKey(source.resourceKey())
        .relatedEntityKey(source.relatedEntityKey())
        .relatedEntityType(source.relatedEntityType())
        .entityDescription(source.entityDescription());
  }
}
