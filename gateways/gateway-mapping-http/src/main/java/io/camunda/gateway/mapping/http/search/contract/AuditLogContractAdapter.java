/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.AuditLogContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogActorTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogCategoryEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogEntityTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogOperationTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogResultEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.search.entities.AuditLogEntity;
import java.util.List;

public final class AuditLogContractAdapter {

  private AuditLogContractAdapter() {}

  public static List<AuditLogContract> adapt(final List<AuditLogEntity> entities) {
    return entities.stream().map(AuditLogContractAdapter::adapt).toList();
  }

  public static AuditLogContract adapt(final AuditLogEntity entity) {
    return AuditLogContract.builder()
        .auditLogKey(
            ContractPolicy.requireNonNull(entity.auditLogKey(), Fields.AUDIT_LOG_KEY, entity))
        .entityKey(ContractPolicy.requireNonNull(entity.entityKey(), Fields.ENTITY_KEY, entity))
        .entityType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.entityType(), AuditLogEntityTypeEnum::fromValue),
                Fields.ENTITY_TYPE,
                entity))
        .operationType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.operationType(), AuditLogOperationTypeEnum::fromValue),
                Fields.OPERATION_TYPE,
                entity))
        .timestamp(
            ContractPolicy.requireNonNull(formatDate(entity.timestamp()), Fields.TIMESTAMP, entity))
        .result(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.result(), AuditLogResultEnum::fromValue),
                Fields.RESULT,
                entity))
        .category(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.category(), AuditLogCategoryEnum::fromValue),
                Fields.CATEGORY,
                entity))
        .batchOperationKey(KeyUtil.keyToString(entity.batchOperationKey()))
        .batchOperationType(
            ContractPolicy.mapEnum(entity.batchOperationType(), BatchOperationTypeEnum::fromValue))
        .actorId(entity.actorId())
        .actorType(ContractPolicy.mapEnum(entity.actorType(), AuditLogActorTypeEnum::fromValue))
        .agentElementId(entity.agentElementId())
        .tenantId(entity.tenantId())
        .processDefinitionId(entity.processDefinitionId())
        .processDefinitionKey(entity.processDefinitionKey())
        .processInstanceKey(entity.processInstanceKey())
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .elementInstanceKey(entity.elementInstanceKey())
        .jobKey(entity.jobKey())
        .userTaskKey(entity.userTaskKey())
        .decisionRequirementsId(entity.decisionRequirementsId())
        .decisionRequirementsKey(entity.decisionRequirementsKey())
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionKey(entity.decisionDefinitionKey())
        .decisionEvaluationKey(entity.decisionEvaluationKey())
        .deploymentKey(entity.deploymentKey())
        .formKey(entity.formKey())
        .resourceKey(KeyUtil.keyToString(entity.resourceKey()))
        .relatedEntityKey(entity.relatedEntityKey())
        .relatedEntityType(
            ContractPolicy.mapEnum(entity.relatedEntityType(), AuditLogEntityTypeEnum::fromValue))
        .entityDescription(entity.entityDescription())
        .build();
  }
}
