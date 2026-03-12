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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionInstanceFilterStrictContract(
    @Nullable Object decisionEvaluationInstanceKey,
    @Nullable Object state,
    @Nullable String evaluationFailure,
    @Nullable Object evaluationDate,
    @Nullable String decisionDefinitionId,
    @Nullable String decisionDefinitionName,
    @Nullable Integer decisionDefinitionVersion,
    @Nullable io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType,
    @Nullable String tenantId,
    @Nullable String decisionEvaluationKey,
    @Nullable String processDefinitionKey,
    @Nullable String processInstanceKey,
    @Nullable Object decisionDefinitionKey,
    @Nullable Object elementInstanceKey,
    @Nullable Object rootDecisionDefinitionKey,
    @Nullable Object decisionRequirementsKey) {

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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object decisionEvaluationInstanceKey;
    private Object state;
    private String evaluationFailure;
    private Object evaluationDate;
    private String decisionDefinitionId;
    private String decisionDefinitionName;
    private Integer decisionDefinitionVersion;
    private io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType;
    private String tenantId;
    private Object decisionEvaluationKey;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object decisionDefinitionKey;
    private Object elementInstanceKey;
    private Object rootDecisionDefinitionKey;
    private Object decisionRequirementsKey;

    private Builder() {}

    @Override
    public OptionalStep decisionEvaluationInstanceKey(final Object decisionEvaluationInstanceKey) {
      this.decisionEvaluationInstanceKey = decisionEvaluationInstanceKey;
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationInstanceKey(
        final Object decisionEvaluationInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionEvaluationInstanceKey =
          policy.apply(
              decisionEvaluationInstanceKey, Fields.DECISION_EVALUATION_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep state(final Object state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep evaluationFailure(final String evaluationFailure) {
      this.evaluationFailure = evaluationFailure;
      return this;
    }

    @Override
    public OptionalStep evaluationFailure(
        final String evaluationFailure, final ContractPolicy.FieldPolicy<String> policy) {
      this.evaluationFailure = policy.apply(evaluationFailure, Fields.EVALUATION_FAILURE, null);
      return this;
    }

    @Override
    public OptionalStep evaluationDate(final Object evaluationDate) {
      this.evaluationDate = evaluationDate;
      return this;
    }

    @Override
    public OptionalStep evaluationDate(
        final Object evaluationDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.evaluationDate = policy.apply(evaluationDate, Fields.EVALUATION_DATE, null);
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
    public OptionalStep decisionDefinitionName(final String decisionDefinitionName) {
      this.decisionDefinitionName = decisionDefinitionName;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionName(
        final String decisionDefinitionName, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionName =
          policy.apply(decisionDefinitionName, Fields.DECISION_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionVersion(final Integer decisionDefinitionVersion) {
      this.decisionDefinitionVersion = decisionDefinitionVersion;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.decisionDefinitionVersion =
          policy.apply(decisionDefinitionVersion, Fields.DECISION_DEFINITION_VERSION, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionType(
        final io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType) {
      this.decisionDefinitionType = decisionDefinitionType;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionType(
        final io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum>
            policy) {
      this.decisionDefinitionType =
          policy.apply(decisionDefinitionType, Fields.DECISION_DEFINITION_TYPE, null);
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
    public OptionalStep decisionDefinitionKey(final Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
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
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootDecisionDefinitionKey(final Object rootDecisionDefinitionKey) {
      this.rootDecisionDefinitionKey = rootDecisionDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep rootDecisionDefinitionKey(
        final Object rootDecisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootDecisionDefinitionKey =
          policy.apply(rootDecisionDefinitionKey, Fields.ROOT_DECISION_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
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
    public GeneratedDecisionInstanceFilterStrictContract build() {
      return new GeneratedDecisionInstanceFilterStrictContract(
          this.decisionEvaluationInstanceKey,
          this.state,
          this.evaluationFailure,
          this.evaluationDate,
          this.decisionDefinitionId,
          this.decisionDefinitionName,
          this.decisionDefinitionVersion,
          this.decisionDefinitionType,
          this.tenantId,
          coerceDecisionEvaluationKey(this.decisionEvaluationKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          this.decisionDefinitionKey,
          this.elementInstanceKey,
          this.rootDecisionDefinitionKey,
          this.decisionRequirementsKey);
    }
  }

  public interface OptionalStep {
    OptionalStep decisionEvaluationInstanceKey(final Object decisionEvaluationInstanceKey);

    OptionalStep decisionEvaluationInstanceKey(
        final Object decisionEvaluationInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep state(final Object state);

    OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep evaluationFailure(final String evaluationFailure);

    OptionalStep evaluationFailure(
        final String evaluationFailure, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep evaluationDate(final Object evaluationDate);

    OptionalStep evaluationDate(
        final Object evaluationDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionDefinitionId(final String decisionDefinitionId);

    OptionalStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionName(final String decisionDefinitionName);

    OptionalStep decisionDefinitionName(
        final String decisionDefinitionName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionVersion(final Integer decisionDefinitionVersion);

    OptionalStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep decisionDefinitionType(
        final io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType);

    OptionalStep decisionDefinitionType(
        final io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum>
            policy);

    OptionalStep tenantId(final String tenantId);

    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionEvaluationKey(final String decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(final Object decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(
        final String decisionEvaluationKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionEvaluationKey(
        final Object decisionEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy);

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

    OptionalStep decisionDefinitionKey(final Object decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootDecisionDefinitionKey(final Object rootDecisionDefinitionKey);

    OptionalStep rootDecisionDefinitionKey(
        final Object rootDecisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedDecisionInstanceFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_INSTANCE_KEY =
        ContractPolicy.field("DecisionInstanceFilter", "decisionEvaluationInstanceKey");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("DecisionInstanceFilter", "state");
    public static final ContractPolicy.FieldRef EVALUATION_FAILURE =
        ContractPolicy.field("DecisionInstanceFilter", "evaluationFailure");
    public static final ContractPolicy.FieldRef EVALUATION_DATE =
        ContractPolicy.field("DecisionInstanceFilter", "evaluationDate");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("DecisionInstanceFilter", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_NAME =
        ContractPolicy.field("DecisionInstanceFilter", "decisionDefinitionName");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_VERSION =
        ContractPolicy.field("DecisionInstanceFilter", "decisionDefinitionVersion");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_TYPE =
        ContractPolicy.field("DecisionInstanceFilter", "decisionDefinitionType");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DecisionInstanceFilter", "tenantId");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_KEY =
        ContractPolicy.field("DecisionInstanceFilter", "decisionEvaluationKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("DecisionInstanceFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("DecisionInstanceFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("DecisionInstanceFilter", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("DecisionInstanceFilter", "elementInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_DECISION_DEFINITION_KEY =
        ContractPolicy.field("DecisionInstanceFilter", "rootDecisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("DecisionInstanceFilter", "decisionRequirementsKey");

    private Fields() {}
  }
}
