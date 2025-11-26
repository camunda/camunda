/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.util.ObjectBuilder;

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
    Long decisionEvaluationKey,
    Long deploymentKey,
    Long formKey,
    Long resourceKey) {

  public static class Builder implements ObjectBuilder<AuditLogEntity> {

    private Long auditLogKey;
    private String entityKey;
    private AuditLogEntityType entityType;
    private AuditLogOperationType operationType;
    private Long batchOperationKey;
    private BatchOperationType batchOperationType;
    private String timestamp;
    private String actorId;
    private AuditLogActorType actorType;
    private String tenantId;
    private AuditLogResult result;
    private String annotation;
    private AuditLogCategory category;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long elementInstanceKey;
    private Long jobKey;
    private Long userTaskKey;
    private String decisionRequirementsId;
    private Long decisionRequirementsKey;
    private String decisionDefinitionId;
    private Long decisionDefinitionKey;
    private Long decisionEvaluationKey;
    private Long deploymentKey;
    private Long formKey;
    private Long resourceKey;

    public Builder auditLogKey(final Long auditLogKey) {
      this.auditLogKey = auditLogKey;
      return this;
    }

    public Builder entityKey(final String entityKey) {
      this.entityKey = entityKey;
      return this;
    }

    public Builder entityType(final AuditLogEntityType entityType) {
      this.entityType = entityType;
      return this;
    }

    public Builder operationType(final AuditLogOperationType operationType) {
      this.operationType = operationType;
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

    public Builder timestamp(final String timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder actorId(final String actorId) {
      this.actorId = actorId;
      return this;
    }

    public Builder actorType(final AuditLogActorType actorType) {
      this.actorType = actorType;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder result(final AuditLogResult result) {
      this.result = result;
      return this;
    }

    public Builder annotation(final String annotation) {
      this.annotation = annotation;
      return this;
    }

    public Builder category(final AuditLogCategory category) {
      this.category = category;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
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

    public Builder decisionRequirementsId(final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    public Builder decisionRequirementsKey(final Long decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    public Builder decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
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

    @Override
    public AuditLogEntity build() {
      return new AuditLogEntity(
          auditLogKey,
          entityKey,
          entityType,
          operationType,
          batchOperationKey,
          batchOperationType,
          timestamp,
          actorId,
          actorType,
          tenantId,
          result,
          annotation,
          category,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          elementInstanceKey,
          jobKey,
          userTaskKey,
          decisionRequirementsId,
          decisionRequirementsKey,
          decisionDefinitionId,
          decisionDefinitionKey,
          decisionEvaluationKey,
          deploymentKey,
          formKey,
          resourceKey);
    }
  }

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
