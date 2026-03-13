/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuditLogFilterStrictContract(
    @Nullable Object auditLogKey,
    @Nullable Object processDefinitionKey,
    @Nullable Object processInstanceKey,
    @Nullable Object elementInstanceKey,
    @Nullable Object operationType,
    @Nullable Object result,
    @Nullable Object timestamp,
    @Nullable Object actorId,
    @Nullable Object actorType,
    @Nullable Object agentElementId,
    @Nullable Object entityKey,
    @Nullable Object entityType,
    @Nullable Object tenantId,
    @Nullable Object category,
    @Nullable Object deploymentKey,
    @Nullable Object formKey,
    @Nullable Object resourceKey,
    @Nullable Object batchOperationType,
    @Nullable Object processDefinitionId,
    @Nullable Object jobKey,
    @Nullable Object userTaskKey,
    @Nullable Object decisionRequirementsId,
    @Nullable Object decisionRequirementsKey,
    @Nullable Object decisionDefinitionId,
    @Nullable Object decisionDefinitionKey,
    @Nullable Object decisionEvaluationKey,
    @Nullable Object relatedEntityKey,
    @Nullable Object relatedEntityType,
    @Nullable Object entityDescription) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object auditLogKey;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object elementInstanceKey;
    private Object operationType;
    private Object result;
    private Object timestamp;
    private Object actorId;
    private Object actorType;
    private Object agentElementId;
    private Object entityKey;
    private Object entityType;
    private Object tenantId;
    private Object category;
    private Object deploymentKey;
    private Object formKey;
    private Object resourceKey;
    private Object batchOperationType;
    private Object processDefinitionId;
    private Object jobKey;
    private Object userTaskKey;
    private Object decisionRequirementsId;
    private Object decisionRequirementsKey;
    private Object decisionDefinitionId;
    private Object decisionDefinitionKey;
    private Object decisionEvaluationKey;
    private Object relatedEntityKey;
    private Object relatedEntityType;
    private Object entityDescription;

    private Builder() {}

    @Override
    public OptionalStep auditLogKey(final @Nullable Object auditLogKey) {
      this.auditLogKey = auditLogKey;
      return this;
    }

    @Override
    public OptionalStep auditLogKey(
        final @Nullable Object auditLogKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.auditLogKey = policy.apply(auditLogKey, Fields.AUDIT_LOG_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep operationType(final @Nullable Object operationType) {
      this.operationType = operationType;
      return this;
    }

    @Override
    public OptionalStep operationType(
        final @Nullable Object operationType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.operationType = policy.apply(operationType, Fields.OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep result(final @Nullable Object result) {
      this.result = result;
      return this;
    }

    @Override
    public OptionalStep result(
        final @Nullable Object result, final ContractPolicy.FieldPolicy<Object> policy) {
      this.result = policy.apply(result, Fields.RESULT, null);
      return this;
    }

    @Override
    public OptionalStep timestamp(final @Nullable Object timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    @Override
    public OptionalStep timestamp(
        final @Nullable Object timestamp, final ContractPolicy.FieldPolicy<Object> policy) {
      this.timestamp = policy.apply(timestamp, Fields.TIMESTAMP, null);
      return this;
    }

    @Override
    public OptionalStep actorId(final @Nullable Object actorId) {
      this.actorId = actorId;
      return this;
    }

    @Override
    public OptionalStep actorId(
        final @Nullable Object actorId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.actorId = policy.apply(actorId, Fields.ACTOR_ID, null);
      return this;
    }

    @Override
    public OptionalStep actorType(final @Nullable Object actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final @Nullable Object actorType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.actorType = policy.apply(actorType, Fields.ACTOR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep agentElementId(final @Nullable Object agentElementId) {
      this.agentElementId = agentElementId;
      return this;
    }

    @Override
    public OptionalStep agentElementId(
        final @Nullable Object agentElementId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.agentElementId = policy.apply(agentElementId, Fields.AGENT_ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep entityKey(final @Nullable Object entityKey) {
      this.entityKey = entityKey;
      return this;
    }

    @Override
    public OptionalStep entityKey(
        final @Nullable Object entityKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.entityKey = policy.apply(entityKey, Fields.ENTITY_KEY, null);
      return this;
    }

    @Override
    public OptionalStep entityType(final @Nullable Object entityType) {
      this.entityType = entityType;
      return this;
    }

    @Override
    public OptionalStep entityType(
        final @Nullable Object entityType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.entityType = policy.apply(entityType, Fields.ENTITY_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable Object tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep category(final @Nullable Object category) {
      this.category = category;
      return this;
    }

    @Override
    public OptionalStep category(
        final @Nullable Object category, final ContractPolicy.FieldPolicy<Object> policy) {
      this.category = policy.apply(category, Fields.CATEGORY, null);
      return this;
    }

    @Override
    public OptionalStep deploymentKey(final @Nullable Object deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    @Override
    public OptionalStep deploymentKey(
        final @Nullable Object deploymentKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.deploymentKey = policy.apply(deploymentKey, Fields.DEPLOYMENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(final @Nullable Object formKey) {
      this.formKey = formKey;
      return this;
    }

    @Override
    public OptionalStep formKey(
        final @Nullable Object formKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep resourceKey(final @Nullable Object resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    @Override
    public OptionalStep resourceKey(
        final @Nullable Object resourceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.resourceKey = policy.apply(resourceKey, Fields.RESOURCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep batchOperationType(final @Nullable Object batchOperationType) {
      this.batchOperationType = batchOperationType;
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final @Nullable Object batchOperationType,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.batchOperationType = policy.apply(batchOperationType, Fields.BATCH_OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final @Nullable Object processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable Object processDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(final @Nullable Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(
        final @Nullable Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final @Nullable Object userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    @Override
    public OptionalStep userTaskKey(
        final @Nullable Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(final @Nullable Object decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(
        final @Nullable Object decisionRequirementsId,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirementsId =
          policy.apply(decisionRequirementsId, Fields.DECISION_REQUIREMENTS_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final @Nullable Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(
        final @Nullable Object decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionId(final @Nullable Object decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionId(
        final @Nullable Object decisionDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinitionId =
          policy.apply(decisionDefinitionId, Fields.DECISION_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(final @Nullable Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(
        final @Nullable Object decisionDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinitionKey =
          policy.apply(decisionDefinitionKey, Fields.DECISION_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationKey(final @Nullable Object decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationKey(
        final @Nullable Object decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionEvaluationKey =
          policy.apply(decisionEvaluationKey, Fields.DECISION_EVALUATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep relatedEntityKey(final @Nullable Object relatedEntityKey) {
      this.relatedEntityKey = relatedEntityKey;
      return this;
    }

    @Override
    public OptionalStep relatedEntityKey(
        final @Nullable Object relatedEntityKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.relatedEntityKey = policy.apply(relatedEntityKey, Fields.RELATED_ENTITY_KEY, null);
      return this;
    }

    @Override
    public OptionalStep relatedEntityType(final @Nullable Object relatedEntityType) {
      this.relatedEntityType = relatedEntityType;
      return this;
    }

    @Override
    public OptionalStep relatedEntityType(
        final @Nullable Object relatedEntityType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.relatedEntityType = policy.apply(relatedEntityType, Fields.RELATED_ENTITY_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep entityDescription(final @Nullable Object entityDescription) {
      this.entityDescription = entityDescription;
      return this;
    }

    @Override
    public OptionalStep entityDescription(
        final @Nullable Object entityDescription, final ContractPolicy.FieldPolicy<Object> policy) {
      this.entityDescription = policy.apply(entityDescription, Fields.ENTITY_DESCRIPTION, null);
      return this;
    }

    @Override
    public GeneratedAuditLogFilterStrictContract build() {
      return new GeneratedAuditLogFilterStrictContract(
          this.auditLogKey,
          this.processDefinitionKey,
          this.processInstanceKey,
          this.elementInstanceKey,
          this.operationType,
          this.result,
          this.timestamp,
          this.actorId,
          this.actorType,
          this.agentElementId,
          this.entityKey,
          this.entityType,
          this.tenantId,
          this.category,
          this.deploymentKey,
          this.formKey,
          this.resourceKey,
          this.batchOperationType,
          this.processDefinitionId,
          this.jobKey,
          this.userTaskKey,
          this.decisionRequirementsId,
          this.decisionRequirementsKey,
          this.decisionDefinitionId,
          this.decisionDefinitionKey,
          this.decisionEvaluationKey,
          this.relatedEntityKey,
          this.relatedEntityType,
          this.entityDescription);
    }
  }

  public interface OptionalStep {
    OptionalStep auditLogKey(final @Nullable Object auditLogKey);

    OptionalStep auditLogKey(
        final @Nullable Object auditLogKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep operationType(final @Nullable Object operationType);

    OptionalStep operationType(
        final @Nullable Object operationType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep result(final @Nullable Object result);

    OptionalStep result(
        final @Nullable Object result, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep timestamp(final @Nullable Object timestamp);

    OptionalStep timestamp(
        final @Nullable Object timestamp, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep actorId(final @Nullable Object actorId);

    OptionalStep actorId(
        final @Nullable Object actorId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep actorType(final @Nullable Object actorType);

    OptionalStep actorType(
        final @Nullable Object actorType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep agentElementId(final @Nullable Object agentElementId);

    OptionalStep agentElementId(
        final @Nullable Object agentElementId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep entityKey(final @Nullable Object entityKey);

    OptionalStep entityKey(
        final @Nullable Object entityKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep entityType(final @Nullable Object entityType);

    OptionalStep entityType(
        final @Nullable Object entityType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tenantId(final @Nullable Object tenantId);

    OptionalStep tenantId(
        final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep category(final @Nullable Object category);

    OptionalStep category(
        final @Nullable Object category, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep deploymentKey(final @Nullable Object deploymentKey);

    OptionalStep deploymentKey(
        final @Nullable Object deploymentKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep formKey(final @Nullable Object formKey);

    OptionalStep formKey(
        final @Nullable Object formKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep resourceKey(final @Nullable Object resourceKey);

    OptionalStep resourceKey(
        final @Nullable Object resourceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep batchOperationType(final @Nullable Object batchOperationType);

    OptionalStep batchOperationType(
        final @Nullable Object batchOperationType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionId(final @Nullable Object processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable Object processDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep jobKey(final @Nullable Object jobKey);

    OptionalStep jobKey(
        final @Nullable Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep userTaskKey(final @Nullable Object userTaskKey);

    OptionalStep userTaskKey(
        final @Nullable Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirementsId(final @Nullable Object decisionRequirementsId);

    OptionalStep decisionRequirementsId(
        final @Nullable Object decisionRequirementsId,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirementsKey(final @Nullable Object decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final @Nullable Object decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionDefinitionId(final @Nullable Object decisionDefinitionId);

    OptionalStep decisionDefinitionId(
        final @Nullable Object decisionDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionDefinitionKey(final @Nullable Object decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(
        final @Nullable Object decisionDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionEvaluationKey(final @Nullable Object decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(
        final @Nullable Object decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep relatedEntityKey(final @Nullable Object relatedEntityKey);

    OptionalStep relatedEntityKey(
        final @Nullable Object relatedEntityKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep relatedEntityType(final @Nullable Object relatedEntityType);

    OptionalStep relatedEntityType(
        final @Nullable Object relatedEntityType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep entityDescription(final @Nullable Object entityDescription);

    OptionalStep entityDescription(
        final @Nullable Object entityDescription, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedAuditLogFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef AUDIT_LOG_KEY =
        ContractPolicy.field("AuditLogFilter", "auditLogKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("AuditLogFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("AuditLogFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("AuditLogFilter", "elementInstanceKey");
    public static final ContractPolicy.FieldRef OPERATION_TYPE =
        ContractPolicy.field("AuditLogFilter", "operationType");
    public static final ContractPolicy.FieldRef RESULT =
        ContractPolicy.field("AuditLogFilter", "result");
    public static final ContractPolicy.FieldRef TIMESTAMP =
        ContractPolicy.field("AuditLogFilter", "timestamp");
    public static final ContractPolicy.FieldRef ACTOR_ID =
        ContractPolicy.field("AuditLogFilter", "actorId");
    public static final ContractPolicy.FieldRef ACTOR_TYPE =
        ContractPolicy.field("AuditLogFilter", "actorType");
    public static final ContractPolicy.FieldRef AGENT_ELEMENT_ID =
        ContractPolicy.field("AuditLogFilter", "agentElementId");
    public static final ContractPolicy.FieldRef ENTITY_KEY =
        ContractPolicy.field("AuditLogFilter", "entityKey");
    public static final ContractPolicy.FieldRef ENTITY_TYPE =
        ContractPolicy.field("AuditLogFilter", "entityType");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("AuditLogFilter", "tenantId");
    public static final ContractPolicy.FieldRef CATEGORY =
        ContractPolicy.field("AuditLogFilter", "category");
    public static final ContractPolicy.FieldRef DEPLOYMENT_KEY =
        ContractPolicy.field("AuditLogFilter", "deploymentKey");
    public static final ContractPolicy.FieldRef FORM_KEY =
        ContractPolicy.field("AuditLogFilter", "formKey");
    public static final ContractPolicy.FieldRef RESOURCE_KEY =
        ContractPolicy.field("AuditLogFilter", "resourceKey");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_TYPE =
        ContractPolicy.field("AuditLogFilter", "batchOperationType");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("AuditLogFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef JOB_KEY =
        ContractPolicy.field("AuditLogFilter", "jobKey");
    public static final ContractPolicy.FieldRef USER_TASK_KEY =
        ContractPolicy.field("AuditLogFilter", "userTaskKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("AuditLogFilter", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("AuditLogFilter", "decisionRequirementsKey");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("AuditLogFilter", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("AuditLogFilter", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_KEY =
        ContractPolicy.field("AuditLogFilter", "decisionEvaluationKey");
    public static final ContractPolicy.FieldRef RELATED_ENTITY_KEY =
        ContractPolicy.field("AuditLogFilter", "relatedEntityKey");
    public static final ContractPolicy.FieldRef RELATED_ENTITY_TYPE =
        ContractPolicy.field("AuditLogFilter", "relatedEntityType");
    public static final ContractPolicy.FieldRef ENTITY_DESCRIPTION =
        ContractPolicy.field("AuditLogFilter", "entityDescription");

    private Fields() {}
  }
}
