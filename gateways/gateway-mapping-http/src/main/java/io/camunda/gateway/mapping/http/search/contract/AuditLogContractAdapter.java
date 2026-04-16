/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.mapEnum;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;

import io.camunda.gateway.protocol.model.AuditLogActorTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogCategoryEnum;
import io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogResult;
import io.camunda.gateway.protocol.model.AuditLogResultEnum;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.search.entities.AuditLogEntity;
import java.util.List;

public final class AuditLogContractAdapter {

  private AuditLogContractAdapter() {}

  public static List<AuditLogResult> adapt(final List<AuditLogEntity> entities) {
    return entities.stream().map(AuditLogContractAdapter::adapt).toList();
  }

  public static AuditLogResult adapt(final AuditLogEntity entity) {
    return new AuditLogResult()
        .auditLogKey(requireNonNull(entity.auditLogKey(), "auditLogKey", entity))
        .entityKey(requireNonNull(entity.entityKey(), "entityKey", entity))
        .entityType(
            requireNonNull(
                mapEnum(entity.entityType(), AuditLogEntityTypeEnum::fromValue),
                "entityType",
                entity))
        .operationType(
            requireNonNull(
                mapEnum(entity.operationType(), AuditLogOperationTypeEnum::fromValue),
                "operationType",
                entity))
        .timestamp(requireNonNull(formatDate(entity.timestamp()), "timestamp", entity))
        .result(
            requireNonNull(
                mapEnum(entity.result(), AuditLogResultEnum::fromValue), "result", entity))
        .category(
            requireNonNull(
                mapEnum(entity.category(), AuditLogCategoryEnum::fromValue), "category", entity))
        .batchOperationKey(keyToString(entity.batchOperationKey()))
        .batchOperationType(mapEnum(entity.batchOperationType(), BatchOperationTypeEnum::fromValue))
        .actorId(entity.actorId())
        .actorType(mapEnum(entity.actorType(), AuditLogActorTypeEnum::fromValue))
        .agentElementId(entity.agentElementId())
        .tenantId(entity.tenantId())
        .processDefinitionId(entity.processDefinitionId())
        .processDefinitionKey(keyToString(entity.processDefinitionKey()))
        .processInstanceKey(keyToString(entity.processInstanceKey()))
        .rootProcessInstanceKey(keyToString(entity.rootProcessInstanceKey()))
        .elementInstanceKey(keyToString(entity.elementInstanceKey()))
        .jobKey(keyToString(entity.jobKey()))
        .userTaskKey(keyToString(entity.userTaskKey()))
        .decisionRequirementsId(entity.decisionRequirementsId())
        .decisionRequirementsKey(keyToString(entity.decisionRequirementsKey()))
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionKey(keyToString(entity.decisionDefinitionKey()))
        .decisionEvaluationKey(keyToString(entity.decisionEvaluationKey()))
        .deploymentKey(keyToString(entity.deploymentKey()))
        .formKey(keyToString(entity.formKey()))
        .resourceKey(keyToString(entity.resourceKey()))
        .relatedEntityKey(entity.relatedEntityKey())
        .relatedEntityType(mapEnum(entity.relatedEntityType(), AuditLogEntityTypeEnum::fromValue))
        .entityDescription(entity.entityDescription());
  }
}
