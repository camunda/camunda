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
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedEvaluateDecisionStrictContract(
    String decisionDefinitionId,
    String decisionDefinitionKey,
    String decisionDefinitionName,
    Integer decisionDefinitionVersion,
    String decisionEvaluationKey,
    String decisionInstanceKey,
    String decisionRequirementsId,
    String decisionRequirementsKey,
    java.util.List<GeneratedEvaluatedDecisionStrictContract> evaluatedDecisions,
    @Nullable String failedDecisionDefinitionId,
    @Nullable String failureMessage,
    String output,
    String tenantId) {

  public GeneratedEvaluateDecisionStrictContract {
    Objects.requireNonNull(
        decisionDefinitionId, "decisionDefinitionId is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionKey, "decisionDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionName, "decisionDefinitionName is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionVersion, "decisionDefinitionVersion is required and must not be null");
    Objects.requireNonNull(
        decisionEvaluationKey, "decisionEvaluationKey is required and must not be null");
    Objects.requireNonNull(
        decisionInstanceKey, "decisionInstanceKey is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsId, "decisionRequirementsId is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsKey, "decisionRequirementsKey is required and must not be null");
    Objects.requireNonNull(
        evaluatedDecisions, "evaluatedDecisions is required and must not be null");
    Objects.requireNonNull(output, "output is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
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

  public static String coerceDecisionInstanceKey(final Object value) {
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
        "decisionInstanceKey must be a String or Number, but was " + value.getClass().getName());
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

  public static java.util.List<GeneratedEvaluatedDecisionStrictContract> coerceEvaluatedDecisions(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "evaluatedDecisions must be a List of GeneratedEvaluatedDecisionStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedEvaluatedDecisionStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedEvaluatedDecisionStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "evaluatedDecisions must contain only GeneratedEvaluatedDecisionStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static DecisionDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements DecisionDefinitionIdStep,
          DecisionDefinitionKeyStep,
          DecisionDefinitionNameStep,
          DecisionDefinitionVersionStep,
          DecisionEvaluationKeyStep,
          DecisionInstanceKeyStep,
          DecisionRequirementsIdStep,
          DecisionRequirementsKeyStep,
          EvaluatedDecisionsStep,
          OutputStep,
          TenantIdStep,
          OptionalStep {
    private String decisionDefinitionId;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionIdPolicy;
    private Object decisionDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> decisionDefinitionKeyPolicy;
    private String decisionDefinitionName;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionNamePolicy;
    private Integer decisionDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> decisionDefinitionVersionPolicy;
    private Object decisionEvaluationKey;
    private ContractPolicy.FieldPolicy<Object> decisionEvaluationKeyPolicy;
    private Object decisionInstanceKey;
    private ContractPolicy.FieldPolicy<Object> decisionInstanceKeyPolicy;
    private String decisionRequirementsId;
    private ContractPolicy.FieldPolicy<String> decisionRequirementsIdPolicy;
    private Object decisionRequirementsKey;
    private ContractPolicy.FieldPolicy<Object> decisionRequirementsKeyPolicy;
    private Object evaluatedDecisions;
    private ContractPolicy.FieldPolicy<Object> evaluatedDecisionsPolicy;
    private String failedDecisionDefinitionId;
    private String failureMessage;
    private String output;
    private ContractPolicy.FieldPolicy<String> outputPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;

    private Builder() {}

    @Override
    public DecisionDefinitionKeyStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionId = decisionDefinitionId;
      this.decisionDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public DecisionDefinitionNameStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      this.decisionDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public DecisionDefinitionVersionStep decisionDefinitionName(
        final String decisionDefinitionName, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionName = decisionDefinitionName;
      this.decisionDefinitionNamePolicy = policy;
      return this;
    }

    @Override
    public DecisionEvaluationKeyStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.decisionDefinitionVersion = decisionDefinitionVersion;
      this.decisionDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public DecisionInstanceKeyStep decisionEvaluationKey(
        final Object decisionEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      this.decisionEvaluationKeyPolicy = policy;
      return this;
    }

    @Override
    public DecisionRequirementsIdStep decisionInstanceKey(
        final Object decisionInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionInstanceKey = decisionInstanceKey;
      this.decisionInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public DecisionRequirementsKeyStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsId = decisionRequirementsId;
      this.decisionRequirementsIdPolicy = policy;
      return this;
    }

    @Override
    public EvaluatedDecisionsStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      this.decisionRequirementsKeyPolicy = policy;
      return this;
    }

    @Override
    public OutputStep evaluatedDecisions(
        final Object evaluatedDecisions, final ContractPolicy.FieldPolicy<Object> policy) {
      this.evaluatedDecisions = evaluatedDecisions;
      this.evaluatedDecisionsPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep output(
        final String output, final ContractPolicy.FieldPolicy<String> policy) {
      this.output = output;
      this.outputPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep failedDecisionDefinitionId(final String failedDecisionDefinitionId) {
      this.failedDecisionDefinitionId = failedDecisionDefinitionId;
      return this;
    }

    @Override
    public OptionalStep failedDecisionDefinitionId(
        final String failedDecisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.failedDecisionDefinitionId =
          policy.apply(failedDecisionDefinitionId, Fields.FAILED_DECISION_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep failureMessage(final String failureMessage) {
      this.failureMessage = failureMessage;
      return this;
    }

    @Override
    public OptionalStep failureMessage(
        final String failureMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.failureMessage = policy.apply(failureMessage, Fields.FAILURE_MESSAGE, null);
      return this;
    }

    @Override
    public GeneratedEvaluateDecisionStrictContract build() {
      return new GeneratedEvaluateDecisionStrictContract(
          applyRequiredPolicy(
              this.decisionDefinitionId,
              this.decisionDefinitionIdPolicy,
              Fields.DECISION_DEFINITION_ID),
          coerceDecisionDefinitionKey(
              applyRequiredPolicy(
                  this.decisionDefinitionKey,
                  this.decisionDefinitionKeyPolicy,
                  Fields.DECISION_DEFINITION_KEY)),
          applyRequiredPolicy(
              this.decisionDefinitionName,
              this.decisionDefinitionNamePolicy,
              Fields.DECISION_DEFINITION_NAME),
          applyRequiredPolicy(
              this.decisionDefinitionVersion,
              this.decisionDefinitionVersionPolicy,
              Fields.DECISION_DEFINITION_VERSION),
          coerceDecisionEvaluationKey(
              applyRequiredPolicy(
                  this.decisionEvaluationKey,
                  this.decisionEvaluationKeyPolicy,
                  Fields.DECISION_EVALUATION_KEY)),
          coerceDecisionInstanceKey(
              applyRequiredPolicy(
                  this.decisionInstanceKey,
                  this.decisionInstanceKeyPolicy,
                  Fields.DECISION_INSTANCE_KEY)),
          applyRequiredPolicy(
              this.decisionRequirementsId,
              this.decisionRequirementsIdPolicy,
              Fields.DECISION_REQUIREMENTS_ID),
          coerceDecisionRequirementsKey(
              applyRequiredPolicy(
                  this.decisionRequirementsKey,
                  this.decisionRequirementsKeyPolicy,
                  Fields.DECISION_REQUIREMENTS_KEY)),
          coerceEvaluatedDecisions(
              applyRequiredPolicy(
                  this.evaluatedDecisions,
                  this.evaluatedDecisionsPolicy,
                  Fields.EVALUATED_DECISIONS)),
          this.failedDecisionDefinitionId,
          this.failureMessage,
          applyRequiredPolicy(this.output, this.outputPolicy, Fields.OUTPUT),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID));
    }
  }

  public interface DecisionDefinitionIdStep {
    DecisionDefinitionKeyStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionDefinitionKeyStep {
    DecisionDefinitionNameStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionDefinitionNameStep {
    DecisionDefinitionVersionStep decisionDefinitionName(
        final String decisionDefinitionName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionDefinitionVersionStep {
    DecisionEvaluationKeyStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface DecisionEvaluationKeyStep {
    DecisionInstanceKeyStep decisionEvaluationKey(
        final Object decisionEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionInstanceKeyStep {
    DecisionRequirementsIdStep decisionInstanceKey(
        final Object decisionInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionRequirementsIdStep {
    DecisionRequirementsKeyStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionRequirementsKeyStep {
    EvaluatedDecisionsStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface EvaluatedDecisionsStep {
    OutputStep evaluatedDecisions(
        final Object evaluatedDecisions, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OutputStep {
    TenantIdStep output(final String output, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep failedDecisionDefinitionId(final String failedDecisionDefinitionId);

    OptionalStep failedDecisionDefinitionId(
        final String failedDecisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep failureMessage(final String failureMessage);

    OptionalStep failureMessage(
        final String failureMessage, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedEvaluateDecisionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("EvaluateDecisionResult", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("EvaluateDecisionResult", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_NAME =
        ContractPolicy.field("EvaluateDecisionResult", "decisionDefinitionName");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_VERSION =
        ContractPolicy.field("EvaluateDecisionResult", "decisionDefinitionVersion");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_KEY =
        ContractPolicy.field("EvaluateDecisionResult", "decisionEvaluationKey");
    public static final ContractPolicy.FieldRef DECISION_INSTANCE_KEY =
        ContractPolicy.field("EvaluateDecisionResult", "decisionInstanceKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("EvaluateDecisionResult", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("EvaluateDecisionResult", "decisionRequirementsKey");
    public static final ContractPolicy.FieldRef EVALUATED_DECISIONS =
        ContractPolicy.field("EvaluateDecisionResult", "evaluatedDecisions");
    public static final ContractPolicy.FieldRef FAILED_DECISION_DEFINITION_ID =
        ContractPolicy.field("EvaluateDecisionResult", "failedDecisionDefinitionId");
    public static final ContractPolicy.FieldRef FAILURE_MESSAGE =
        ContractPolicy.field("EvaluateDecisionResult", "failureMessage");
    public static final ContractPolicy.FieldRef OUTPUT =
        ContractPolicy.field("EvaluateDecisionResult", "output");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("EvaluateDecisionResult", "tenantId");

    private Fields() {}
  }
}
