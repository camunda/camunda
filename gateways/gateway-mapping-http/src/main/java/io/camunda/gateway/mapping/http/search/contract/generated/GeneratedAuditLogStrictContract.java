/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuditLogStrictContract(
    String auditLogKey,
    String entityKey,
    io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum entityType,
    io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum operationType,
    @Nullable String batchOperationKey,
    @Nullable io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType,
    String timestamp,
    @Nullable String actorId,
    @Nullable io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType,
    @Nullable String agentElementId,
    @Nullable String tenantId,
    io.camunda.gateway.protocol.model.AuditLogResultEnum result,
    @Nullable String annotation,
    io.camunda.gateway.protocol.model.AuditLogCategoryEnum category,
    @Nullable String processDefinitionId,
    @Nullable String processDefinitionKey,
    @Nullable String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    @Nullable String elementInstanceKey,
    @Nullable String jobKey,
    @Nullable String userTaskKey,
    @Nullable String decisionRequirementsId,
    @Nullable String decisionRequirementsKey,
    @Nullable String decisionDefinitionId,
    @Nullable String decisionDefinitionKey,
    @Nullable String decisionEvaluationKey,
    @Nullable String deploymentKey,
    @Nullable String formKey,
    @Nullable String resourceKey,
    @Nullable String relatedEntityKey,
    @Nullable io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum relatedEntityType,
    @Nullable String entityDescription) {

  public GeneratedAuditLogStrictContract {
    Objects.requireNonNull(auditLogKey, "auditLogKey is required and must not be null");
    Objects.requireNonNull(entityKey, "entityKey is required and must not be null");
    Objects.requireNonNull(entityType, "entityType is required and must not be null");
    Objects.requireNonNull(operationType, "operationType is required and must not be null");
    Objects.requireNonNull(timestamp, "timestamp is required and must not be null");
    Objects.requireNonNull(result, "result is required and must not be null");
    Objects.requireNonNull(category, "category is required and must not be null");
  }

  public static String coerceAuditLogKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "auditLogKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceProcessDefinitionKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "processDefinitionKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceProcessInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "processInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceRootProcessInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "rootProcessInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceElementInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "elementInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceJobKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "jobKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceUserTaskKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "userTaskKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceDecisionRequirementsKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "decisionRequirementsKey must be a String or Number, but was "
            + value.getClass().getName());
  }

  public static String coerceDecisionDefinitionKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "decisionDefinitionKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceDecisionEvaluationKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "decisionEvaluationKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceDeploymentKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "deploymentKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceFormKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "formKey must be a String or Number, but was " + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static AuditLogKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements AuditLogKeyStep,
          EntityKeyStep,
          EntityTypeStep,
          OperationTypeStep,
          TimestampStep,
          ResultStep,
          CategoryStep,
          OptionalStep {
    private Object auditLogKey;
    private ContractPolicy.FieldPolicy<Object> auditLogKeyPolicy;
    private String entityKey;
    private ContractPolicy.FieldPolicy<String> entityKeyPolicy;
    private io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum entityType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum>
        entityTypePolicy;
    private io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum operationType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum>
        operationTypePolicy;
    private String batchOperationKey;
    private io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType;
    private String timestamp;
    private ContractPolicy.FieldPolicy<String> timestampPolicy;
    private String actorId;
    private io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType;
    private String agentElementId;
    private String tenantId;
    private io.camunda.gateway.protocol.model.AuditLogResultEnum result;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogResultEnum>
        resultPolicy;
    private String annotation;
    private io.camunda.gateway.protocol.model.AuditLogCategoryEnum category;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogCategoryEnum>
        categoryPolicy;
    private String processDefinitionId;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private Object elementInstanceKey;
    private Object jobKey;
    private Object userTaskKey;
    private String decisionRequirementsId;
    private Object decisionRequirementsKey;
    private String decisionDefinitionId;
    private Object decisionDefinitionKey;
    private Object decisionEvaluationKey;
    private Object deploymentKey;
    private Object formKey;
    private String resourceKey;
    private String relatedEntityKey;
    private io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum relatedEntityType;
    private String entityDescription;

    private Builder() {}

    @Override
    public EntityKeyStep auditLogKey(
        final Object auditLogKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.auditLogKey = auditLogKey;
      this.auditLogKeyPolicy = policy;
      return this;
    }

    @Override
    public EntityTypeStep entityKey(
        final String entityKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.entityKey = entityKey;
      this.entityKeyPolicy = policy;
      return this;
    }

    @Override
    public OperationTypeStep entityType(
        final io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum entityType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum>
            policy) {
      this.entityType = entityType;
      this.entityTypePolicy = policy;
      return this;
    }

    @Override
    public TimestampStep operationType(
        final io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum operationType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum>
            policy) {
      this.operationType = operationType;
      this.operationTypePolicy = policy;
      return this;
    }

    @Override
    public ResultStep timestamp(
        final String timestamp, final ContractPolicy.FieldPolicy<String> policy) {
      this.timestamp = timestamp;
      this.timestampPolicy = policy;
      return this;
    }

    @Override
    public CategoryStep result(
        final io.camunda.gateway.protocol.model.AuditLogResultEnum result,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogResultEnum>
            policy) {
      this.result = result;
      this.resultPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep category(
        final io.camunda.gateway.protocol.model.AuditLogCategoryEnum category,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogCategoryEnum>
            policy) {
      this.category = category;
      this.categoryPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep batchOperationKey(final String batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    @Override
    public OptionalStep batchOperationKey(
        final String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.batchOperationKey = policy.apply(batchOperationKey, Fields.BATCH_OPERATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType) {
      this.batchOperationType = batchOperationType;
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
            policy) {
      this.batchOperationType = policy.apply(batchOperationType, Fields.BATCH_OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep actorId(final String actorId) {
      this.actorId = actorId;
      return this;
    }

    @Override
    public OptionalStep actorId(
        final String actorId, final ContractPolicy.FieldPolicy<String> policy) {
      this.actorId = policy.apply(actorId, Fields.ACTOR_ID, null);
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogActorTypeEnum>
            policy) {
      this.actorType = policy.apply(actorType, Fields.ACTOR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep agentElementId(final String agentElementId) {
      this.agentElementId = agentElementId;
      return this;
    }

    @Override
    public OptionalStep agentElementId(
        final String agentElementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.agentElementId = policy.apply(agentElementId, Fields.AGENT_ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep annotation(final String annotation) {
      this.annotation = annotation;
      return this;
    }

    @Override
    public OptionalStep annotation(
        final String annotation, final ContractPolicy.FieldPolicy<String> policy) {
      this.annotation = policy.apply(annotation, Fields.ANNOTATION, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final String elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(final String jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(final Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder jobKey(final String jobKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(
        final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final String userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final Object userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    public Builder userTaskKey(
        final String userTaskKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(
        final Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsId =
          policy.apply(decisionRequirementsId, Fields.DECISION_REQUIREMENTS_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final String decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    public Builder decisionRequirementsKey(
        final String decisionRequirementsKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionId =
          policy.apply(decisionDefinitionId, Fields.DECISION_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(final String decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(final Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    public Builder decisionDefinitionKey(
        final String decisionDefinitionKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionKey =
          policy.apply(decisionDefinitionKey, Fields.DECISION_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinitionKey =
          policy.apply(decisionDefinitionKey, Fields.DECISION_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationKey(final String decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationKey(final Object decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    public Builder decisionEvaluationKey(
        final String decisionEvaluationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionEvaluationKey =
          policy.apply(decisionEvaluationKey, Fields.DECISION_EVALUATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationKey(
        final Object decisionEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionEvaluationKey =
          policy.apply(decisionEvaluationKey, Fields.DECISION_EVALUATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep deploymentKey(final String deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    @Override
    public OptionalStep deploymentKey(final Object deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    public Builder deploymentKey(
        final String deploymentKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.deploymentKey = policy.apply(deploymentKey, Fields.DEPLOYMENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep deploymentKey(
        final Object deploymentKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.deploymentKey = policy.apply(deploymentKey, Fields.DEPLOYMENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(final String formKey) {
      this.formKey = formKey;
      return this;
    }

    @Override
    public OptionalStep formKey(final Object formKey) {
      this.formKey = formKey;
      return this;
    }

    public Builder formKey(final String formKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(
        final Object formKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep resourceKey(final String resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    @Override
    public OptionalStep resourceKey(
        final String resourceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceKey = policy.apply(resourceKey, Fields.RESOURCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep relatedEntityKey(final String relatedEntityKey) {
      this.relatedEntityKey = relatedEntityKey;
      return this;
    }

    @Override
    public OptionalStep relatedEntityKey(
        final String relatedEntityKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.relatedEntityKey = policy.apply(relatedEntityKey, Fields.RELATED_ENTITY_KEY, null);
      return this;
    }

    @Override
    public OptionalStep relatedEntityType(
        final io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum relatedEntityType) {
      this.relatedEntityType = relatedEntityType;
      return this;
    }

    @Override
    public OptionalStep relatedEntityType(
        final io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum relatedEntityType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum>
            policy) {
      this.relatedEntityType = policy.apply(relatedEntityType, Fields.RELATED_ENTITY_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep entityDescription(final String entityDescription) {
      this.entityDescription = entityDescription;
      return this;
    }

    @Override
    public OptionalStep entityDescription(
        final String entityDescription, final ContractPolicy.FieldPolicy<String> policy) {
      this.entityDescription = policy.apply(entityDescription, Fields.ENTITY_DESCRIPTION, null);
      return this;
    }

    @Override
    public GeneratedAuditLogStrictContract build() {
      return new GeneratedAuditLogStrictContract(
          coerceAuditLogKey(
              applyRequiredPolicy(this.auditLogKey, this.auditLogKeyPolicy, Fields.AUDIT_LOG_KEY)),
          applyRequiredPolicy(this.entityKey, this.entityKeyPolicy, Fields.ENTITY_KEY),
          applyRequiredPolicy(this.entityType, this.entityTypePolicy, Fields.ENTITY_TYPE),
          applyRequiredPolicy(this.operationType, this.operationTypePolicy, Fields.OPERATION_TYPE),
          this.batchOperationKey,
          this.batchOperationType,
          applyRequiredPolicy(this.timestamp, this.timestampPolicy, Fields.TIMESTAMP),
          this.actorId,
          this.actorType,
          this.agentElementId,
          this.tenantId,
          applyRequiredPolicy(this.result, this.resultPolicy, Fields.RESULT),
          this.annotation,
          applyRequiredPolicy(this.category, this.categoryPolicy, Fields.CATEGORY),
          this.processDefinitionId,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceElementInstanceKey(this.elementInstanceKey),
          coerceJobKey(this.jobKey),
          coerceUserTaskKey(this.userTaskKey),
          this.decisionRequirementsId,
          coerceDecisionRequirementsKey(this.decisionRequirementsKey),
          this.decisionDefinitionId,
          coerceDecisionDefinitionKey(this.decisionDefinitionKey),
          coerceDecisionEvaluationKey(this.decisionEvaluationKey),
          coerceDeploymentKey(this.deploymentKey),
          coerceFormKey(this.formKey),
          this.resourceKey,
          this.relatedEntityKey,
          this.relatedEntityType,
          this.entityDescription);
    }
  }

  public interface AuditLogKeyStep {
    EntityKeyStep auditLogKey(
        final Object auditLogKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface EntityKeyStep {
    EntityTypeStep entityKey(
        final String entityKey, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface EntityTypeStep {
    OperationTypeStep entityType(
        final io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum entityType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum>
            policy);
  }

  public interface OperationTypeStep {
    TimestampStep operationType(
        final io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum operationType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum>
            policy);
  }

  public interface TimestampStep {
    ResultStep timestamp(final String timestamp, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ResultStep {
    CategoryStep result(
        final io.camunda.gateway.protocol.model.AuditLogResultEnum result,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogResultEnum>
            policy);
  }

  public interface CategoryStep {
    OptionalStep category(
        final io.camunda.gateway.protocol.model.AuditLogCategoryEnum category,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogCategoryEnum>
            policy);
  }

  public interface OptionalStep {
    OptionalStep batchOperationKey(final String batchOperationKey);

    OptionalStep batchOperationKey(
        final String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType);

    OptionalStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
            policy);

    OptionalStep actorId(final String actorId);

    OptionalStep actorId(final String actorId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep actorType(final io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType);

    OptionalStep actorType(
        final io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogActorTypeEnum>
            policy);

    OptionalStep agentElementId(final String agentElementId);

    OptionalStep agentElementId(
        final String agentElementId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep tenantId(final String tenantId);

    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep annotation(final String annotation);

    OptionalStep annotation(
        final String annotation, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionId(final String processDefinitionId);

    OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(final String processDefinitionKey);

    OptionalStep processDefinitionKey(final Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final String processInstanceKey);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final String elementInstanceKey);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep jobKey(final String jobKey);

    OptionalStep jobKey(final Object jobKey);

    OptionalStep jobKey(final String jobKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep jobKey(final Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep userTaskKey(final String userTaskKey);

    OptionalStep userTaskKey(final Object userTaskKey);

    OptionalStep userTaskKey(
        final String userTaskKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep userTaskKey(
        final Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirementsId(final String decisionRequirementsId);

    OptionalStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(final String decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final String decisionRequirementsKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionDefinitionId(final String decisionDefinitionId);

    OptionalStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionKey(final String decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(final Object decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(
        final String decisionDefinitionKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionEvaluationKey(final String decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(final Object decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(
        final String decisionEvaluationKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionEvaluationKey(
        final Object decisionEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep deploymentKey(final String deploymentKey);

    OptionalStep deploymentKey(final Object deploymentKey);

    OptionalStep deploymentKey(
        final String deploymentKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep deploymentKey(
        final Object deploymentKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep formKey(final String formKey);

    OptionalStep formKey(final Object formKey);

    OptionalStep formKey(final String formKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep formKey(final Object formKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep resourceKey(final String resourceKey);

    OptionalStep resourceKey(
        final String resourceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep relatedEntityKey(final String relatedEntityKey);

    OptionalStep relatedEntityKey(
        final String relatedEntityKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep relatedEntityType(
        final io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum relatedEntityType);

    OptionalStep relatedEntityType(
        final io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum relatedEntityType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum>
            policy);

    OptionalStep entityDescription(final String entityDescription);

    OptionalStep entityDescription(
        final String entityDescription, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAuditLogStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef AUDIT_LOG_KEY =
        ContractPolicy.field("AuditLogResult", "auditLogKey");
    public static final ContractPolicy.FieldRef ENTITY_KEY =
        ContractPolicy.field("AuditLogResult", "entityKey");
    public static final ContractPolicy.FieldRef ENTITY_TYPE =
        ContractPolicy.field("AuditLogResult", "entityType");
    public static final ContractPolicy.FieldRef OPERATION_TYPE =
        ContractPolicy.field("AuditLogResult", "operationType");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_KEY =
        ContractPolicy.field("AuditLogResult", "batchOperationKey");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_TYPE =
        ContractPolicy.field("AuditLogResult", "batchOperationType");
    public static final ContractPolicy.FieldRef TIMESTAMP =
        ContractPolicy.field("AuditLogResult", "timestamp");
    public static final ContractPolicy.FieldRef ACTOR_ID =
        ContractPolicy.field("AuditLogResult", "actorId");
    public static final ContractPolicy.FieldRef ACTOR_TYPE =
        ContractPolicy.field("AuditLogResult", "actorType");
    public static final ContractPolicy.FieldRef AGENT_ELEMENT_ID =
        ContractPolicy.field("AuditLogResult", "agentElementId");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("AuditLogResult", "tenantId");
    public static final ContractPolicy.FieldRef RESULT =
        ContractPolicy.field("AuditLogResult", "result");
    public static final ContractPolicy.FieldRef ANNOTATION =
        ContractPolicy.field("AuditLogResult", "annotation");
    public static final ContractPolicy.FieldRef CATEGORY =
        ContractPolicy.field("AuditLogResult", "category");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("AuditLogResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("AuditLogResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("AuditLogResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("AuditLogResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("AuditLogResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef JOB_KEY =
        ContractPolicy.field("AuditLogResult", "jobKey");
    public static final ContractPolicy.FieldRef USER_TASK_KEY =
        ContractPolicy.field("AuditLogResult", "userTaskKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("AuditLogResult", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("AuditLogResult", "decisionRequirementsKey");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("AuditLogResult", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("AuditLogResult", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_KEY =
        ContractPolicy.field("AuditLogResult", "decisionEvaluationKey");
    public static final ContractPolicy.FieldRef DEPLOYMENT_KEY =
        ContractPolicy.field("AuditLogResult", "deploymentKey");
    public static final ContractPolicy.FieldRef FORM_KEY =
        ContractPolicy.field("AuditLogResult", "formKey");
    public static final ContractPolicy.FieldRef RESOURCE_KEY =
        ContractPolicy.field("AuditLogResult", "resourceKey");
    public static final ContractPolicy.FieldRef RELATED_ENTITY_KEY =
        ContractPolicy.field("AuditLogResult", "relatedEntityKey");
    public static final ContractPolicy.FieldRef RELATED_ENTITY_TYPE =
        ContractPolicy.field("AuditLogResult", "relatedEntityType");
    public static final ContractPolicy.FieldRef ENTITY_DESCRIPTION =
        ContractPolicy.field("AuditLogResult", "entityDescription");

    private Fields() {}
  }
}
