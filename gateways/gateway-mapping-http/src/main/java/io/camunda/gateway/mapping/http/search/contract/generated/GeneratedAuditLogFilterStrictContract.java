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
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuditLogFilterStrictContract(
    @JsonProperty("auditLogKey")
        @Nullable GeneratedAuditLogKeyFilterPropertyStrictContract auditLogKey,
    @JsonProperty("processDefinitionKey")
        @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey,
    @JsonProperty("processInstanceKey")
        @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
    @JsonProperty("elementInstanceKey")
        @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
    @JsonProperty("operationType")
        @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType,
    @JsonProperty("result") @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result,
    @JsonProperty("timestamp") @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp,
    @JsonProperty("actorId") @Nullable GeneratedStringFilterPropertyStrictContract actorId,
    @JsonProperty("actorType")
        @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType,
    @JsonProperty("agentElementId")
        @Nullable GeneratedStringFilterPropertyStrictContract agentElementId,
    @JsonProperty("entityKey")
        @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract entityKey,
    @JsonProperty("entityType")
        @Nullable GeneratedEntityTypeFilterPropertyStrictContract entityType,
    @JsonProperty("tenantId") @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
    @JsonProperty("category") @Nullable GeneratedCategoryFilterPropertyStrictContract category,
    @JsonProperty("deploymentKey")
        @Nullable GeneratedDeploymentKeyFilterPropertyStrictContract deploymentKey,
    @JsonProperty("formKey") @Nullable GeneratedFormKeyFilterPropertyStrictContract formKey,
    @JsonProperty("resourceKey")
        @Nullable GeneratedResourceKeyFilterPropertyStrictContract resourceKey,
    @JsonProperty("batchOperationType")
        @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract batchOperationType,
    @JsonProperty("processDefinitionId")
        @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
    @JsonProperty("jobKey") @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey,
    @JsonProperty("userTaskKey")
        @Nullable GeneratedBasicStringFilterPropertyStrictContract userTaskKey,
    @JsonProperty("decisionRequirementsId")
        @Nullable GeneratedStringFilterPropertyStrictContract decisionRequirementsId,
    @JsonProperty("decisionRequirementsKey")
        @Nullable GeneratedDecisionRequirementsKeyFilterPropertyStrictContract
            decisionRequirementsKey,
    @JsonProperty("decisionDefinitionId")
        @Nullable GeneratedStringFilterPropertyStrictContract decisionDefinitionId,
    @JsonProperty("decisionDefinitionKey")
        @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract decisionDefinitionKey,
    @JsonProperty("decisionEvaluationKey")
        @Nullable GeneratedDecisionEvaluationKeyFilterPropertyStrictContract decisionEvaluationKey,
    @JsonProperty("relatedEntityKey")
        @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract relatedEntityKey,
    @JsonProperty("relatedEntityType")
        @Nullable GeneratedEntityTypeFilterPropertyStrictContract relatedEntityType,
    @JsonProperty("entityDescription")
        @Nullable GeneratedStringFilterPropertyStrictContract entityDescription) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedAuditLogKeyFilterPropertyStrictContract auditLogKey;
    private GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey;
    private GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey;
    private GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey;
    private GeneratedOperationTypeFilterPropertyStrictContract operationType;
    private GeneratedAuditLogResultFilterPropertyStrictContract result;
    private GeneratedDateTimeFilterPropertyStrictContract timestamp;
    private GeneratedStringFilterPropertyStrictContract actorId;
    private GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType;
    private GeneratedStringFilterPropertyStrictContract agentElementId;
    private GeneratedAuditLogEntityKeyFilterPropertyStrictContract entityKey;
    private GeneratedEntityTypeFilterPropertyStrictContract entityType;
    private GeneratedStringFilterPropertyStrictContract tenantId;
    private GeneratedCategoryFilterPropertyStrictContract category;
    private GeneratedDeploymentKeyFilterPropertyStrictContract deploymentKey;
    private GeneratedFormKeyFilterPropertyStrictContract formKey;
    private GeneratedResourceKeyFilterPropertyStrictContract resourceKey;
    private GeneratedBatchOperationTypeFilterPropertyStrictContract batchOperationType;
    private GeneratedStringFilterPropertyStrictContract processDefinitionId;
    private GeneratedJobKeyFilterPropertyStrictContract jobKey;
    private GeneratedBasicStringFilterPropertyStrictContract userTaskKey;
    private GeneratedStringFilterPropertyStrictContract decisionRequirementsId;
    private GeneratedDecisionRequirementsKeyFilterPropertyStrictContract decisionRequirementsKey;
    private GeneratedStringFilterPropertyStrictContract decisionDefinitionId;
    private GeneratedDecisionDefinitionKeyFilterPropertyStrictContract decisionDefinitionKey;
    private GeneratedDecisionEvaluationKeyFilterPropertyStrictContract decisionEvaluationKey;
    private GeneratedAuditLogEntityKeyFilterPropertyStrictContract relatedEntityKey;
    private GeneratedEntityTypeFilterPropertyStrictContract relatedEntityType;
    private GeneratedStringFilterPropertyStrictContract entityDescription;

    private Builder() {}

    @Override
    public OptionalStep auditLogKey(
        final @Nullable GeneratedAuditLogKeyFilterPropertyStrictContract auditLogKey) {
      this.auditLogKey = auditLogKey;
      return this;
    }

    @Override
    public OptionalStep auditLogKey(
        final @Nullable GeneratedAuditLogKeyFilterPropertyStrictContract auditLogKey,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogKeyFilterPropertyStrictContract> policy) {
      this.auditLogKey = policy.apply(auditLogKey, Fields.AUDIT_LOG_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionKeyFilterPropertyStrictContract>
            policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract
            processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract
            elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep operationType(
        final @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType) {
      this.operationType = operationType;
      return this;
    }

    @Override
    public OptionalStep operationType(
        final @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType,
        final ContractPolicy.FieldPolicy<GeneratedOperationTypeFilterPropertyStrictContract>
            policy) {
      this.operationType = policy.apply(operationType, Fields.OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep result(
        final @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result) {
      this.result = result;
      return this;
    }

    @Override
    public OptionalStep result(
        final @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogResultFilterPropertyStrictContract>
            policy) {
      this.result = policy.apply(result, Fields.RESULT, null);
      return this;
    }

    @Override
    public OptionalStep timestamp(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    @Override
    public OptionalStep timestamp(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.timestamp = policy.apply(timestamp, Fields.TIMESTAMP, null);
      return this;
    }

    @Override
    public OptionalStep actorId(
        final @Nullable GeneratedStringFilterPropertyStrictContract actorId) {
      this.actorId = actorId;
      return this;
    }

    @Override
    public OptionalStep actorId(
        final @Nullable GeneratedStringFilterPropertyStrictContract actorId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.actorId = policy.apply(actorId, Fields.ACTOR_ID, null);
      return this;
    }

    @Override
    public OptionalStep actorType(
        final @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogActorTypeFilterPropertyStrictContract>
            policy) {
      this.actorType = policy.apply(actorType, Fields.ACTOR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep agentElementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract agentElementId) {
      this.agentElementId = agentElementId;
      return this;
    }

    @Override
    public OptionalStep agentElementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract agentElementId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.agentElementId = policy.apply(agentElementId, Fields.AGENT_ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep entityKey(
        final @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract entityKey) {
      this.entityKey = entityKey;
      return this;
    }

    @Override
    public OptionalStep entityKey(
        final @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract entityKey,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogEntityKeyFilterPropertyStrictContract>
            policy) {
      this.entityKey = policy.apply(entityKey, Fields.ENTITY_KEY, null);
      return this;
    }

    @Override
    public OptionalStep entityType(
        final @Nullable GeneratedEntityTypeFilterPropertyStrictContract entityType) {
      this.entityType = entityType;
      return this;
    }

    @Override
    public OptionalStep entityType(
        final @Nullable GeneratedEntityTypeFilterPropertyStrictContract entityType,
        final ContractPolicy.FieldPolicy<GeneratedEntityTypeFilterPropertyStrictContract> policy) {
      this.entityType = policy.apply(entityType, Fields.ENTITY_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep category(
        final @Nullable GeneratedCategoryFilterPropertyStrictContract category) {
      this.category = category;
      return this;
    }

    @Override
    public OptionalStep category(
        final @Nullable GeneratedCategoryFilterPropertyStrictContract category,
        final ContractPolicy.FieldPolicy<GeneratedCategoryFilterPropertyStrictContract> policy) {
      this.category = policy.apply(category, Fields.CATEGORY, null);
      return this;
    }

    @Override
    public OptionalStep deploymentKey(
        final @Nullable GeneratedDeploymentKeyFilterPropertyStrictContract deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    @Override
    public OptionalStep deploymentKey(
        final @Nullable GeneratedDeploymentKeyFilterPropertyStrictContract deploymentKey,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentKeyFilterPropertyStrictContract>
            policy) {
      this.deploymentKey = policy.apply(deploymentKey, Fields.DEPLOYMENT_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(
        final @Nullable GeneratedFormKeyFilterPropertyStrictContract formKey) {
      this.formKey = formKey;
      return this;
    }

    @Override
    public OptionalStep formKey(
        final @Nullable GeneratedFormKeyFilterPropertyStrictContract formKey,
        final ContractPolicy.FieldPolicy<GeneratedFormKeyFilterPropertyStrictContract> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep resourceKey(
        final @Nullable GeneratedResourceKeyFilterPropertyStrictContract resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    @Override
    public OptionalStep resourceKey(
        final @Nullable GeneratedResourceKeyFilterPropertyStrictContract resourceKey,
        final ContractPolicy.FieldPolicy<GeneratedResourceKeyFilterPropertyStrictContract> policy) {
      this.resourceKey = policy.apply(resourceKey, Fields.RESOURCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract
            batchOperationType) {
      this.batchOperationType = batchOperationType;
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract batchOperationType,
        final ContractPolicy.FieldPolicy<GeneratedBatchOperationTypeFilterPropertyStrictContract>
            policy) {
      this.batchOperationType = policy.apply(batchOperationType, Fields.BATCH_OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep jobKey(final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    @Override
    public OptionalStep jobKey(
        final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey,
        final ContractPolicy.FieldPolicy<GeneratedJobKeyFilterPropertyStrictContract> policy) {
      this.jobKey = policy.apply(jobKey, Fields.JOB_KEY, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    @Override
    public OptionalStep userTaskKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract userTaskKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(
        final @Nullable GeneratedStringFilterPropertyStrictContract decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(
        final @Nullable GeneratedStringFilterPropertyStrictContract decisionRequirementsId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.decisionRequirementsId =
          policy.apply(decisionRequirementsId, Fields.DECISION_REQUIREMENTS_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(
        final @Nullable GeneratedDecisionRequirementsKeyFilterPropertyStrictContract
            decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(
        final @Nullable GeneratedDecisionRequirementsKeyFilterPropertyStrictContract
            decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<
                GeneratedDecisionRequirementsKeyFilterPropertyStrictContract>
            policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract decisionDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.decisionDefinitionId =
          policy.apply(decisionDefinitionId, Fields.DECISION_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            decisionDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedDecisionDefinitionKeyFilterPropertyStrictContract>
            policy) {
      this.decisionDefinitionKey =
          policy.apply(decisionDefinitionKey, Fields.DECISION_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationKey(
        final @Nullable GeneratedDecisionEvaluationKeyFilterPropertyStrictContract
            decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationKey(
        final @Nullable GeneratedDecisionEvaluationKeyFilterPropertyStrictContract
            decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<GeneratedDecisionEvaluationKeyFilterPropertyStrictContract>
            policy) {
      this.decisionEvaluationKey =
          policy.apply(decisionEvaluationKey, Fields.DECISION_EVALUATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep relatedEntityKey(
        final @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract relatedEntityKey) {
      this.relatedEntityKey = relatedEntityKey;
      return this;
    }

    @Override
    public OptionalStep relatedEntityKey(
        final @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract relatedEntityKey,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogEntityKeyFilterPropertyStrictContract>
            policy) {
      this.relatedEntityKey = policy.apply(relatedEntityKey, Fields.RELATED_ENTITY_KEY, null);
      return this;
    }

    @Override
    public OptionalStep relatedEntityType(
        final @Nullable GeneratedEntityTypeFilterPropertyStrictContract relatedEntityType) {
      this.relatedEntityType = relatedEntityType;
      return this;
    }

    @Override
    public OptionalStep relatedEntityType(
        final @Nullable GeneratedEntityTypeFilterPropertyStrictContract relatedEntityType,
        final ContractPolicy.FieldPolicy<GeneratedEntityTypeFilterPropertyStrictContract> policy) {
      this.relatedEntityType = policy.apply(relatedEntityType, Fields.RELATED_ENTITY_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep entityDescription(
        final @Nullable GeneratedStringFilterPropertyStrictContract entityDescription) {
      this.entityDescription = entityDescription;
      return this;
    }

    @Override
    public OptionalStep entityDescription(
        final @Nullable GeneratedStringFilterPropertyStrictContract entityDescription,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
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
    OptionalStep auditLogKey(
        final @Nullable GeneratedAuditLogKeyFilterPropertyStrictContract auditLogKey);

    OptionalStep auditLogKey(
        final @Nullable GeneratedAuditLogKeyFilterPropertyStrictContract auditLogKey,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogKeyFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep operationType(
        final @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType);

    OptionalStep operationType(
        final @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType,
        final ContractPolicy.FieldPolicy<GeneratedOperationTypeFilterPropertyStrictContract>
            policy);

    OptionalStep result(final @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result);

    OptionalStep result(
        final @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogResultFilterPropertyStrictContract>
            policy);

    OptionalStep timestamp(final @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp);

    OptionalStep timestamp(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep actorId(final @Nullable GeneratedStringFilterPropertyStrictContract actorId);

    OptionalStep actorId(
        final @Nullable GeneratedStringFilterPropertyStrictContract actorId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep actorType(
        final @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType);

    OptionalStep actorType(
        final @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogActorTypeFilterPropertyStrictContract>
            policy);

    OptionalStep agentElementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract agentElementId);

    OptionalStep agentElementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract agentElementId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep entityKey(
        final @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract entityKey);

    OptionalStep entityKey(
        final @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract entityKey,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogEntityKeyFilterPropertyStrictContract>
            policy);

    OptionalStep entityType(
        final @Nullable GeneratedEntityTypeFilterPropertyStrictContract entityType);

    OptionalStep entityType(
        final @Nullable GeneratedEntityTypeFilterPropertyStrictContract entityType,
        final ContractPolicy.FieldPolicy<GeneratedEntityTypeFilterPropertyStrictContract> policy);

    OptionalStep tenantId(final @Nullable GeneratedStringFilterPropertyStrictContract tenantId);

    OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep category(final @Nullable GeneratedCategoryFilterPropertyStrictContract category);

    OptionalStep category(
        final @Nullable GeneratedCategoryFilterPropertyStrictContract category,
        final ContractPolicy.FieldPolicy<GeneratedCategoryFilterPropertyStrictContract> policy);

    OptionalStep deploymentKey(
        final @Nullable GeneratedDeploymentKeyFilterPropertyStrictContract deploymentKey);

    OptionalStep deploymentKey(
        final @Nullable GeneratedDeploymentKeyFilterPropertyStrictContract deploymentKey,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentKeyFilterPropertyStrictContract>
            policy);

    OptionalStep formKey(final @Nullable GeneratedFormKeyFilterPropertyStrictContract formKey);

    OptionalStep formKey(
        final @Nullable GeneratedFormKeyFilterPropertyStrictContract formKey,
        final ContractPolicy.FieldPolicy<GeneratedFormKeyFilterPropertyStrictContract> policy);

    OptionalStep resourceKey(
        final @Nullable GeneratedResourceKeyFilterPropertyStrictContract resourceKey);

    OptionalStep resourceKey(
        final @Nullable GeneratedResourceKeyFilterPropertyStrictContract resourceKey,
        final ContractPolicy.FieldPolicy<GeneratedResourceKeyFilterPropertyStrictContract> policy);

    OptionalStep batchOperationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract batchOperationType);

    OptionalStep batchOperationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract batchOperationType,
        final ContractPolicy.FieldPolicy<GeneratedBatchOperationTypeFilterPropertyStrictContract>
            policy);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep jobKey(final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey);

    OptionalStep jobKey(
        final @Nullable GeneratedJobKeyFilterPropertyStrictContract jobKey,
        final ContractPolicy.FieldPolicy<GeneratedJobKeyFilterPropertyStrictContract> policy);

    OptionalStep userTaskKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract userTaskKey);

    OptionalStep userTaskKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract userTaskKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy);

    OptionalStep decisionRequirementsId(
        final @Nullable GeneratedStringFilterPropertyStrictContract decisionRequirementsId);

    OptionalStep decisionRequirementsId(
        final @Nullable GeneratedStringFilterPropertyStrictContract decisionRequirementsId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep decisionRequirementsKey(
        final @Nullable GeneratedDecisionRequirementsKeyFilterPropertyStrictContract
            decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final @Nullable GeneratedDecisionRequirementsKeyFilterPropertyStrictContract
            decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<
                GeneratedDecisionRequirementsKeyFilterPropertyStrictContract>
            policy);

    OptionalStep decisionDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract decisionDefinitionId);

    OptionalStep decisionDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract decisionDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep decisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            decisionDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedDecisionDefinitionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep decisionEvaluationKey(
        final @Nullable GeneratedDecisionEvaluationKeyFilterPropertyStrictContract
            decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(
        final @Nullable GeneratedDecisionEvaluationKeyFilterPropertyStrictContract
            decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<GeneratedDecisionEvaluationKeyFilterPropertyStrictContract>
            policy);

    OptionalStep relatedEntityKey(
        final @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract relatedEntityKey);

    OptionalStep relatedEntityKey(
        final @Nullable GeneratedAuditLogEntityKeyFilterPropertyStrictContract relatedEntityKey,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogEntityKeyFilterPropertyStrictContract>
            policy);

    OptionalStep relatedEntityType(
        final @Nullable GeneratedEntityTypeFilterPropertyStrictContract relatedEntityType);

    OptionalStep relatedEntityType(
        final @Nullable GeneratedEntityTypeFilterPropertyStrictContract relatedEntityType,
        final ContractPolicy.FieldPolicy<GeneratedEntityTypeFilterPropertyStrictContract> policy);

    OptionalStep entityDescription(
        final @Nullable GeneratedStringFilterPropertyStrictContract entityDescription);

    OptionalStep entityDescription(
        final @Nullable GeneratedStringFilterPropertyStrictContract entityDescription,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

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
