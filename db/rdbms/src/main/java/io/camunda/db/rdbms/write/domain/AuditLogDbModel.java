/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.function.Function;

public record AuditLogDbModel(
    String auditLogKey,
    String entityKey,
    AuditLogEntity.AuditLogEntityType entityType,
    AuditLogEntity.AuditLogOperationType operationType,
    Integer entityVersion,
    Short entityValueType,
    Short entityOperationIntent,
    Long batchOperationKey,
    BatchOperationType batchOperationType,
    OffsetDateTime timestamp,
    AuditLogEntity.AuditLogActorType actorType,
    String actorId,
    String agentElementId,
    String tenantId,
    AuditLogEntity.AuditLogTenantScope tenantScope,
    AuditLogEntity.AuditLogOperationResult result,
    String annotation,
    AuditLogEntity.AuditLogOperationCategory category,
    String processDefinitionId,
    String decisionRequirementsId,
    String decisionDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    Long elementInstanceKey,
    Long jobKey,
    Long userTaskKey,
    Long decisionRequirementsKey,
    Long decisionDefinitionKey,
    Long decisionEvaluationKey,
    Long deploymentKey,
    Long formKey,
    Long resourceKey,
    AuditLogEntity.AuditLogEntityType relatedEntityType,
    String relatedEntityKey,
    String entityDescription,
    int partitionId,
    OffsetDateTime historyCleanupDate)
    implements DbModel<AuditLogDbModel> {

  @Override
  public AuditLogDbModel copy(
      final Function<ObjectBuilder<AuditLogDbModel>, ObjectBuilder<AuditLogDbModel>> copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .auditLogKey(auditLogKey)
        .entityKey(entityKey)
        .entityType(entityType)
        .operationType(operationType)
        .entityVersion(entityVersion)
        .entityValueType(entityValueType)
        .entityOperationIntent(entityOperationIntent)
        .batchOperationKey(batchOperationKey)
        .batchOperationType(batchOperationType)
        .timestamp(timestamp)
        .actorType(actorType)
        .actorId(actorId)
        .agentElementId(agentElementId)
        .tenantId(tenantId)
        .tenantScope(tenantScope)
        .result(result)
        .annotation(annotation)
        .category(category)
        .processDefinitionId(processDefinitionId)
        .decisionRequirementsId(decisionRequirementsId)
        .decisionDefinitionId(decisionDefinitionId)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .jobKey(jobKey)
        .userTaskKey(userTaskKey)
        .decisionRequirementsKey(decisionRequirementsKey)
        .decisionDefinitionKey(decisionDefinitionKey)
        .decisionEvaluationKey(decisionEvaluationKey)
        .deploymentKey(deploymentKey)
        .formKey(formKey)
        .resourceKey(resourceKey)
        .relatedEntityType(relatedEntityType)
        .relatedEntityKey(relatedEntityKey)
        .entityDescription(entityDescription)
        .partitionId(partitionId)
        .historyCleanupDate(historyCleanupDate)
        .rootProcessInstanceKey(rootProcessInstanceKey);
  }

  public AuditLogDbModel truncateEntityDescription(final int sizeLimit, final Integer byteLimit) {
    if (TruncateUtil.shouldTruncate(entityDescription(), sizeLimit, byteLimit)) {

      return copy(
          fn ->
              ((Builder) fn)
                  .entityDescription(
                      TruncateUtil.truncateValue(entityDescription(), sizeLimit, byteLimit)));
    }
    return this;
  }

  public static class Builder implements ObjectBuilder<AuditLogDbModel> {

    private String auditLogKey;
    private String entityKey;
    private AuditLogEntity.AuditLogEntityType entityType;
    private AuditLogEntity.AuditLogOperationType operationType;
    private Integer entityVersion;
    private Short entityValueType;
    private Short entityOperationIntent;
    private Long batchOperationKey;
    private BatchOperationType batchOperationType;
    private OffsetDateTime timestamp;
    private AuditLogEntity.AuditLogActorType actorType;
    private String actorId;
    private String agentElementId;
    private String tenantId;
    private AuditLogEntity.AuditLogTenantScope tenantScope;
    private AuditLogEntity.AuditLogOperationResult result;
    private String annotation;
    private AuditLogEntity.AuditLogOperationCategory category;
    private String processDefinitionId;
    private String decisionRequirementsId;
    private String decisionDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private Long elementInstanceKey;
    private Long jobKey;
    private Long userTaskKey;
    private Long decisionRequirementsKey;
    private Long decisionDefinitionKey;
    private Long decisionEvaluationKey;
    private Long deploymentKey;
    private Long formKey;
    private Long resourceKey;
    private AuditLogEntity.AuditLogEntityType relatedEntityType;
    private String relatedEntityKey;
    private String entityDescription;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

    public Builder() {}

    public Builder auditLogKey(final String auditLogKey) {
      this.auditLogKey = auditLogKey;
      return this;
    }

    public Builder entityKey(final String entityKey) {
      this.entityKey = entityKey;
      return this;
    }

    public Builder entityType(final AuditLogEntity.AuditLogEntityType entityType) {
      this.entityType = entityType;
      return this;
    }

    public Builder operationType(final AuditLogEntity.AuditLogOperationType operationType) {
      this.operationType = operationType;
      return this;
    }

    public Builder entityVersion(final Integer entityVersion) {
      this.entityVersion = entityVersion;
      return this;
    }

    public Builder entityValueType(final Short entityValueType) {
      this.entityValueType = entityValueType;
      return this;
    }

    public Builder entityOperationIntent(final Short entityOperationIntent) {
      this.entityOperationIntent = entityOperationIntent;
      return this;
    }

    public Builder batchOperationKey(final Long batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    public Builder batchOperationType(final BatchOperationType batchOperationType) {
      this.batchOperationType = batchOperationType;
      return this;
    }

    public Builder timestamp(final OffsetDateTime timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder actorType(final AuditLogEntity.AuditLogActorType actorType) {
      this.actorType = actorType;
      return this;
    }

    public Builder actorId(final String actorId) {
      this.actorId = actorId;
      return this;
    }

    public Builder agentElementId(final String agentElementId) {
      this.agentElementId = agentElementId;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder tenantScope(final AuditLogEntity.AuditLogTenantScope tenantScope) {
      this.tenantScope = tenantScope;
      return this;
    }

    public Builder result(final AuditLogEntity.AuditLogOperationResult result) {
      this.result = result;
      return this;
    }

    public Builder annotation(final String annotation) {
      this.annotation = annotation;
      return this;
    }

    public Builder category(final AuditLogEntity.AuditLogOperationCategory category) {
      this.category = category;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder decisionRequirementsId(final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    public Builder decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(final Long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder jobKey(final Long jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder userTaskKey(final Long userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    public Builder decisionRequirementsKey(final Long decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    public Builder decisionDefinitionKey(final Long decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    public Builder decisionEvaluationKey(final Long decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    public Builder deploymentKey(final Long deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    public Builder formKey(final Long formKey) {
      this.formKey = formKey;
      return this;
    }

    public Builder resourceKey(final Long resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    public Builder relatedEntityType(final AuditLogEntity.AuditLogEntityType relatedEntityType) {
      this.relatedEntityType = relatedEntityType;
      return this;
    }

    public Builder relatedEntityKey(final String relatedEntityKey) {
      this.relatedEntityKey = relatedEntityKey;
      return this;
    }

    public Builder entityDescription(final String entityDescription) {
      this.entityDescription = entityDescription;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
      this.historyCleanupDate = historyCleanupDate;
      return this;
    }

    @Override
    public AuditLogDbModel build() {
      return new AuditLogDbModel(
          auditLogKey,
          entityKey,
          entityType,
          operationType,
          entityVersion,
          entityValueType,
          entityOperationIntent,
          batchOperationKey,
          batchOperationType,
          timestamp,
          actorType,
          actorId,
          agentElementId,
          tenantId,
          tenantScope,
          result,
          annotation,
          category,
          processDefinitionId,
          decisionRequirementsId,
          decisionDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          rootProcessInstanceKey,
          elementInstanceKey,
          jobKey,
          userTaskKey,
          decisionRequirementsKey,
          decisionDefinitionKey,
          decisionEvaluationKey,
          deploymentKey,
          formKey,
          resourceKey,
          relatedEntityType,
          relatedEntityKey,
          entityDescription,
          partitionId,
          historyCleanupDate);
    }
  }
}
