/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.AuditLog;
import io.camunda.gateway.protocol.model.AuditLogActorTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogCategoryEnum;
import io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogResultEnum;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.search.entities.AuditLogEntity;
import java.util.List;

public final class AuditLogContractAdapter {

  private AuditLogContractAdapter() {}

  public static List<AuditLog> adapt(final List<AuditLogEntity> entities) {
    return entities.stream().map(AuditLogContractAdapter::adapt).toList();
  }

  public static AuditLog adapt(final AuditLogEntity entity) {
    return new AuditLog()
        .auditLogKey(ContractPolicy.requireNonNull(entity.auditLogKey(), "auditLogKey", entity))
        .entityKey(ContractPolicy.requireNonNull(entity.entityKey(), "entityKey", entity))
        .entityType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.entityType(), AuditLogEntityTypeEnum::fromValue),
                "entityType",
                entity))
        .operationType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.operationType(), AuditLogOperationTypeEnum::fromValue),
                "operationType",
                entity))
        .timestamp(
            ContractPolicy.requireNonNull(formatDate(entity.timestamp()), "timestamp", entity))
        .result(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.result(), AuditLogResultEnum::fromValue),
                "result",
                entity))
        .category(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.category(), AuditLogCategoryEnum::fromValue),
                "category",
                entity))
        .batchOperationKey(KeyUtil.keyToString(entity.batchOperationKey()))
        .batchOperationType(
            ContractPolicy.mapEnum(entity.batchOperationType(), BatchOperationTypeEnum::fromValue))
        .actorId(entity.actorId())
        .actorType(ContractPolicy.mapEnum(entity.actorType(), AuditLogActorTypeEnum::fromValue))
        .agentElementId(entity.agentElementId())
        .tenantId(entity.tenantId())
        .processDefinitionId(entity.processDefinitionId())
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(entity.processInstanceKey()))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .elementInstanceKey(KeyUtil.keyToString(entity.elementInstanceKey()))
        .jobKey(KeyUtil.keyToString(entity.jobKey()))
        .userTaskKey(KeyUtil.keyToString(entity.userTaskKey()))
        .decisionRequirementsId(entity.decisionRequirementsId())
        .decisionRequirementsKey(KeyUtil.keyToString(entity.decisionRequirementsKey()))
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionKey(KeyUtil.keyToString(entity.decisionDefinitionKey()))
        .decisionEvaluationKey(KeyUtil.keyToString(entity.decisionEvaluationKey()))
        .deploymentKey(KeyUtil.keyToString(entity.deploymentKey()))
        .formKey(KeyUtil.keyToString(entity.formKey()))
        .resourceKey(KeyUtil.keyToString(entity.resourceKey()))
        .relatedEntityKey(entity.relatedEntityKey())
        .relatedEntityType(
            ContractPolicy.mapEnum(entity.relatedEntityType(), AuditLogEntityTypeEnum::fromValue))
        .entityDescription(entity.entityDescription());
  }
}
