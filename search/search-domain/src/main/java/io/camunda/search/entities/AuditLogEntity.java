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
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditLogEntity(
    String auditLogKey,
    String entityKey,
    AuditLogEntityType entityType,
    AuditLogOperationType operationType,
    Long batchOperationKey,
    BatchOperationType batchOperationType,
    OffsetDateTime timestamp,
    String actorId,
    AuditLogActorType actorType,
    String agentElementId,
    String tenantId,
    AuditLogTenantScope tenantScope,
    AuditLogOperationResult result,
    String annotation,
    AuditLogOperationCategory category,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
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
    Long resourceKey,
    AuditLogEntityType relatedEntityType,
    String relatedEntityKey,
    String entityDescription)
    implements TenantOwnedEntity {

  @Override
  public boolean hasTenantScope() {
    return AuditLogTenantScope.TENANT.equals(tenantScope);
  }

  public static class Builder implements ObjectBuilder<AuditLogEntity> {
    private String auditLogKey;
    private String entityKey;
    private AuditLogEntityType entityType;
    private AuditLogOperationType operationType;
    private Long batchOperationKey;
    private BatchOperationType batchOperationType;
    private OffsetDateTime timestamp;
    private String actorId;
    private AuditLogActorType actorType;
    private String agentElementId;
    private String tenantId;
    private AuditLogTenantScope tenantScope;
    private AuditLogOperationResult result;
    private String annotation;
    private AuditLogOperationCategory category;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
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
    private AuditLogEntityType relatedEntityType;
    private String relatedEntityKey;
    private String entityDescription;

    public Builder auditLogKey(final String auditLogKey) {
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

    public Builder timestamp(final OffsetDateTime timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder actorType(final AuditLogActorType actorType) {
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

    public Builder tenantScope(final AuditLogTenantScope tenantScope) {
      this.tenantScope = tenantScope;
      return this;
    }

    public Builder result(final AuditLogOperationResult result) {
      this.result = result;
      return this;
    }

    public Builder annotation(final String annotation) {
      this.annotation = annotation;
      return this;
    }

    public Builder category(final AuditLogOperationCategory category) {
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

    public Builder relatedEntityType(final AuditLogEntityType relatedEntityType) {
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
          agentElementId,
          tenantId,
          tenantScope,
          result,
          annotation,
          category,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          rootProcessInstanceKey,
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
          resourceKey,
          relatedEntityType,
          relatedEntityKey,
          entityDescription);
    }
  }

  public enum AuditLogActorType {
    USER,
    CLIENT,
    ANONYMOUS,
    UNKNOWN
  }

  public enum AuditLogTenantScope {
    GLOBAL,
    TENANT
  }

  public enum AuditLogEntityType {
    UNKNOWN,
    PROCESS_INSTANCE,
    VARIABLE,
    INCIDENT,
    USER_TASK,
    DECISION,
    BATCH,
    USER,
    MAPPING_RULE,
    ROLE,
    GROUP,
    TENANT,
    AUTHORIZATION,
    RESOURCE,
    CLIENT
  }

  public enum AuditLogOperationCategory {
    UNKNOWN,
    ADMIN,
    DEPLOYED_RESOURCES,
    USER_TASKS
  }

  public enum AuditLogOperationResult {
    SUCCESS,
    FAIL
  }

  public enum AuditLogOperationType {
    UNKNOWN,
    CREATE,
    UPDATE,
    DELETE,
    ASSIGN,
    UNASSIGN,
    MODIFY,
    MIGRATE,
    CANCEL,
    RESOLVE,
    COMPLETE,
    SUSPEND,
    RESUME,
    EVALUATE
  }
}
