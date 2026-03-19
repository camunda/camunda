/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuditLogStrictContract(
    @JsonProperty("auditLogKey") String auditLogKey,
    @JsonProperty("entityKey") String entityKey,
    @JsonProperty("entityType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogEntityTypeEnum
            entityType,
    @JsonProperty("operationType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogOperationTypeEnum
            operationType,
    @JsonProperty("batchOperationKey") @Nullable String batchOperationKey,
    @JsonProperty("batchOperationType")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationTypeEnum
            batchOperationType,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("actorId") @Nullable String actorId,
    @JsonProperty("actorType")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType,
    @JsonProperty("agentElementId") @Nullable String agentElementId,
    @JsonProperty("tenantId") @Nullable String tenantId,
    @JsonProperty("result")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogResultEnum
            result,
    @JsonProperty("annotation") @Nullable String annotation,
    @JsonProperty("category")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogCategoryEnum
            category,
    @JsonProperty("processDefinitionId") @Nullable String processDefinitionId,
    @JsonProperty("processDefinitionKey") @Nullable String processDefinitionKey,
    @JsonProperty("processInstanceKey") @Nullable String processInstanceKey,
    @JsonProperty("rootProcessInstanceKey") @Nullable String rootProcessInstanceKey,
    @JsonProperty("elementInstanceKey") @Nullable String elementInstanceKey,
    @JsonProperty("jobKey") @Nullable String jobKey,
    @JsonProperty("userTaskKey") @Nullable String userTaskKey,
    @JsonProperty("decisionRequirementsId") @Nullable String decisionRequirementsId,
    @JsonProperty("decisionRequirementsKey") @Nullable String decisionRequirementsKey,
    @JsonProperty("decisionDefinitionId") @Nullable String decisionDefinitionId,
    @JsonProperty("decisionDefinitionKey") @Nullable String decisionDefinitionKey,
    @JsonProperty("decisionEvaluationKey") @Nullable String decisionEvaluationKey,
    @JsonProperty("deploymentKey") @Nullable String deploymentKey,
    @JsonProperty("formKey") @Nullable String formKey,
    @JsonProperty("resourceKey") @Nullable String resourceKey,
    @JsonProperty("relatedEntityKey") @Nullable String relatedEntityKey,
    @JsonProperty("relatedEntityType")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogEntityTypeEnum
            relatedEntityType,
    @JsonProperty("entityDescription") @Nullable String entityDescription) {

  public GeneratedAuditLogStrictContract {
    Objects.requireNonNull(auditLogKey, "No auditLogKey provided.");
    Objects.requireNonNull(entityKey, "No entityKey provided.");
    Objects.requireNonNull(entityType, "No entityType provided.");
    Objects.requireNonNull(operationType, "No operationType provided.");
    Objects.requireNonNull(timestamp, "No timestamp provided.");
    Objects.requireNonNull(result, "No result provided.");
    Objects.requireNonNull(category, "No category provided.");
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
    private String entityKey;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedAuditLogEntityTypeEnum
        entityType;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedAuditLogOperationTypeEnum
        operationType;
    private String batchOperationKey;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedBatchOperationTypeEnum
        batchOperationType;
    private String timestamp;
    private String actorId;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogActorTypeEnum
        actorType;
    private String agentElementId;
    private String tenantId;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogResultEnum
        result;
    private String annotation;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogCategoryEnum
        category;
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
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedAuditLogEntityTypeEnum
        relatedEntityType;
    private String entityDescription;

    private Builder() {}

    @Override
    public EntityKeyStep auditLogKey(final Object auditLogKey) {
      this.auditLogKey = auditLogKey;
      return this;
    }

    @Override
    public EntityTypeStep entityKey(final String entityKey) {
      this.entityKey = entityKey;
      return this;
    }

    @Override
    public OperationTypeStep entityType(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedAuditLogEntityTypeEnum
            entityType) {
      this.entityType = entityType;
      return this;
    }

    @Override
    public TimestampStep operationType(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedAuditLogOperationTypeEnum
            operationType) {
      this.operationType = operationType;
      return this;
    }

    @Override
    public ResultStep timestamp(final String timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    @Override
    public CategoryStep result(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogResultEnum
            result) {
      this.result = result;
      return this;
    }

    @Override
    public OptionalStep category(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedAuditLogCategoryEnum
            category) {
      this.category = category;
      return this;
    }

    @Override
    public OptionalStep batchOperationKey(final @Nullable String batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    @Override
    public OptionalStep batchOperationKey(
        final @Nullable String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.batchOperationKey = policy.apply(batchOperationKey, Fields.BATCH_OPERATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationTypeEnum
            batchOperationType) {
      this.batchOperationType = batchOperationType;
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationTypeEnum
            batchOperationType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationTypeEnum>
            policy) {
      this.batchOperationType = policy.apply(batchOperationType, Fields.BATCH_OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep actorId(final @Nullable String actorId) {
      this.actorId = actorId;
      return this;
    }

    @Override
    public OptionalStep actorId(
        final @Nullable String actorId, final ContractPolicy.FieldPolicy<String> policy) {
      this.actorId = policy.apply(actorId, Fields.ACTOR_ID, null);
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedAuditLogActorTypeEnum>
            policy) {
      this.actorType = policy.apply(actorType, Fields.ACTOR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep agentElementId(final @Nullable String agentElementId) {
      this.agentElementId = agentElementId;
      return this;
    }

    @Override
    public OptionalStep agentElementId(
        final @Nullable String agentElementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.agentElementId = policy.apply(agentElementId, Fields.AGENT_ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep annotation(final @Nullable String annotation) {
      this.annotation = annotation;
      return this;
    }

    @Override
    public OptionalStep annotation(
        final @Nullable String annotation, final ContractPolicy.FieldPolicy<String> policy) {
      this.annotation = policy.apply(annotation, Fields.ANNOTATION, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final @Nullable String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable String processDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
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
    public OptionalStep processInstanceKey(final @Nullable String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final @Nullable String processInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
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
    public OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(
        final @Nullable String elementInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
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
    public OptionalStep jobKey(final @Nullable String jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(final @Nullable Object jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder jobKey(
        final @Nullable String jobKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(
        final @Nullable Object jobKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final @Nullable String userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final @Nullable Object userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    public Builder userTaskKey(
        final @Nullable String userTaskKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(
        final @Nullable Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(final @Nullable String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(
        final @Nullable String decisionRequirementsId,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsId =
          policy.apply(decisionRequirementsId, Fields.DECISION_REQUIREMENTS_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final @Nullable String decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final @Nullable Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    public Builder decisionRequirementsKey(
        final @Nullable String decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
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
    public OptionalStep decisionDefinitionId(final @Nullable String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionId(
        final @Nullable String decisionDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionId =
          policy.apply(decisionDefinitionId, Fields.DECISION_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(final @Nullable String decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(final @Nullable Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    public Builder decisionDefinitionKey(
        final @Nullable String decisionDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionKey =
          policy.apply(decisionDefinitionKey, Fields.DECISION_DEFINITION_KEY, null);
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
    public OptionalStep decisionEvaluationKey(final @Nullable String decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationKey(final @Nullable Object decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    public Builder decisionEvaluationKey(
        final @Nullable String decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionEvaluationKey =
          policy.apply(decisionEvaluationKey, Fields.DECISION_EVALUATION_KEY, null);
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
    public OptionalStep deploymentKey(final @Nullable String deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    @Override
    public OptionalStep deploymentKey(final @Nullable Object deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    public Builder deploymentKey(
        final @Nullable String deploymentKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.deploymentKey = policy.apply(deploymentKey, Fields.DEPLOYMENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep deploymentKey(
        final @Nullable Object deploymentKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.deploymentKey = policy.apply(deploymentKey, Fields.DEPLOYMENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(final @Nullable String formKey) {
      this.formKey = formKey;
      return this;
    }

    @Override
    public OptionalStep formKey(final @Nullable Object formKey) {
      this.formKey = formKey;
      return this;
    }

    public Builder formKey(
        final @Nullable String formKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(
        final @Nullable Object formKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep resourceKey(final @Nullable String resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    @Override
    public OptionalStep resourceKey(
        final @Nullable String resourceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceKey = policy.apply(resourceKey, Fields.RESOURCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep relatedEntityKey(final @Nullable String relatedEntityKey) {
      this.relatedEntityKey = relatedEntityKey;
      return this;
    }

    @Override
    public OptionalStep relatedEntityKey(
        final @Nullable String relatedEntityKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.relatedEntityKey = policy.apply(relatedEntityKey, Fields.RELATED_ENTITY_KEY, null);
      return this;
    }

    @Override
    public OptionalStep relatedEntityType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogEntityTypeEnum
            relatedEntityType) {
      this.relatedEntityType = relatedEntityType;
      return this;
    }

    @Override
    public OptionalStep relatedEntityType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogEntityTypeEnum
            relatedEntityType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedAuditLogEntityTypeEnum>
            policy) {
      this.relatedEntityType = policy.apply(relatedEntityType, Fields.RELATED_ENTITY_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep entityDescription(final @Nullable String entityDescription) {
      this.entityDescription = entityDescription;
      return this;
    }

    @Override
    public OptionalStep entityDescription(
        final @Nullable String entityDescription, final ContractPolicy.FieldPolicy<String> policy) {
      this.entityDescription = policy.apply(entityDescription, Fields.ENTITY_DESCRIPTION, null);
      return this;
    }

    @Override
    public GeneratedAuditLogStrictContract build() {
      return new GeneratedAuditLogStrictContract(
          coerceAuditLogKey(this.auditLogKey),
          this.entityKey,
          this.entityType,
          this.operationType,
          this.batchOperationKey,
          this.batchOperationType,
          this.timestamp,
          this.actorId,
          this.actorType,
          this.agentElementId,
          this.tenantId,
          this.result,
          this.annotation,
          this.category,
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
    EntityKeyStep auditLogKey(final Object auditLogKey);
  }

  public interface EntityKeyStep {
    EntityTypeStep entityKey(final String entityKey);
  }

  public interface EntityTypeStep {
    OperationTypeStep entityType(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedAuditLogEntityTypeEnum
            entityType);
  }

  public interface OperationTypeStep {
    TimestampStep operationType(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedAuditLogOperationTypeEnum
            operationType);
  }

  public interface TimestampStep {
    ResultStep timestamp(final String timestamp);
  }

  public interface ResultStep {
    CategoryStep result(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogResultEnum
            result);
  }

  public interface CategoryStep {
    OptionalStep category(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedAuditLogCategoryEnum
            category);
  }

  public interface OptionalStep {
    OptionalStep batchOperationKey(final @Nullable String batchOperationKey);

    OptionalStep batchOperationKey(
        final @Nullable String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep batchOperationType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationTypeEnum
            batchOperationType);

    OptionalStep batchOperationType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationTypeEnum
            batchOperationType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationTypeEnum>
            policy);

    OptionalStep actorId(final @Nullable String actorId);

    OptionalStep actorId(
        final @Nullable String actorId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep actorType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType);

    OptionalStep actorType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedAuditLogActorTypeEnum>
            policy);

    OptionalStep agentElementId(final @Nullable String agentElementId);

    OptionalStep agentElementId(
        final @Nullable String agentElementId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep annotation(final @Nullable String annotation);

    OptionalStep annotation(
        final @Nullable String annotation, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionId(final @Nullable String processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable String processDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final @Nullable String processInstanceKey);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey);

    OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep jobKey(final @Nullable String jobKey);

    OptionalStep jobKey(final @Nullable Object jobKey);

    OptionalStep jobKey(
        final @Nullable String jobKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep jobKey(
        final @Nullable Object jobKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep userTaskKey(final @Nullable String userTaskKey);

    OptionalStep userTaskKey(final @Nullable Object userTaskKey);

    OptionalStep userTaskKey(
        final @Nullable String userTaskKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep userTaskKey(
        final @Nullable Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirementsId(final @Nullable String decisionRequirementsId);

    OptionalStep decisionRequirementsId(
        final @Nullable String decisionRequirementsId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(final @Nullable String decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(final @Nullable Object decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final @Nullable String decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(
        final @Nullable Object decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionDefinitionId(final @Nullable String decisionDefinitionId);

    OptionalStep decisionDefinitionId(
        final @Nullable String decisionDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionKey(final @Nullable String decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(final @Nullable Object decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(
        final @Nullable String decisionDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionKey(
        final @Nullable Object decisionDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionEvaluationKey(final @Nullable String decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(final @Nullable Object decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(
        final @Nullable String decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionEvaluationKey(
        final @Nullable Object decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep deploymentKey(final @Nullable String deploymentKey);

    OptionalStep deploymentKey(final @Nullable Object deploymentKey);

    OptionalStep deploymentKey(
        final @Nullable String deploymentKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep deploymentKey(
        final @Nullable Object deploymentKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep formKey(final @Nullable String formKey);

    OptionalStep formKey(final @Nullable Object formKey);

    OptionalStep formKey(
        final @Nullable String formKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep formKey(
        final @Nullable Object formKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep resourceKey(final @Nullable String resourceKey);

    OptionalStep resourceKey(
        final @Nullable String resourceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep relatedEntityKey(final @Nullable String relatedEntityKey);

    OptionalStep relatedEntityKey(
        final @Nullable String relatedEntityKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep relatedEntityType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogEntityTypeEnum
            relatedEntityType);

    OptionalStep relatedEntityType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogEntityTypeEnum
            relatedEntityType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedAuditLogEntityTypeEnum>
            policy);

    OptionalStep entityDescription(final @Nullable String entityDescription);

    OptionalStep entityDescription(
        final @Nullable String entityDescription, final ContractPolicy.FieldPolicy<String> policy);

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
