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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedEvaluatedDecisionStrictContract(
    String decisionDefinitionId,
    String decisionDefinitionName,
    Integer decisionDefinitionVersion,
    String decisionDefinitionType,
    String output,
    String tenantId,
    java.util.List<GeneratedMatchedDecisionRuleItemStrictContract> matchedRules,
    java.util.List<GeneratedEvaluatedDecisionInputItemStrictContract> evaluatedInputs,
    String decisionDefinitionKey,
    String decisionEvaluationInstanceKey) {

  public GeneratedEvaluatedDecisionStrictContract {
    Objects.requireNonNull(
        decisionDefinitionId, "decisionDefinitionId is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionName, "decisionDefinitionName is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionVersion, "decisionDefinitionVersion is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionType, "decisionDefinitionType is required and must not be null");
    Objects.requireNonNull(output, "output is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(matchedRules, "matchedRules is required and must not be null");
    Objects.requireNonNull(evaluatedInputs, "evaluatedInputs is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionKey, "decisionDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        decisionEvaluationInstanceKey,
        "decisionEvaluationInstanceKey is required and must not be null");
  }

  public static java.util.List<GeneratedMatchedDecisionRuleItemStrictContract> coerceMatchedRules(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "matchedRules must be a List of GeneratedMatchedDecisionRuleItemStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedMatchedDecisionRuleItemStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedMatchedDecisionRuleItemStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "matchedRules must contain only GeneratedMatchedDecisionRuleItemStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static java.util.List<GeneratedEvaluatedDecisionInputItemStrictContract>
      coerceEvaluatedInputs(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "evaluatedInputs must be a List of GeneratedEvaluatedDecisionInputItemStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedEvaluatedDecisionInputItemStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedEvaluatedDecisionInputItemStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "evaluatedInputs must contain only GeneratedEvaluatedDecisionInputItemStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
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

  public static String coerceDecisionEvaluationInstanceKey(final Object value) {
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
        "decisionEvaluationInstanceKey must be a String or Number, but was "
            + value.getClass().getName());
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
          DecisionDefinitionNameStep,
          DecisionDefinitionVersionStep,
          DecisionDefinitionTypeStep,
          OutputStep,
          TenantIdStep,
          MatchedRulesStep,
          EvaluatedInputsStep,
          DecisionDefinitionKeyStep,
          DecisionEvaluationInstanceKeyStep,
          OptionalStep {
    private String decisionDefinitionId;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionIdPolicy;
    private String decisionDefinitionName;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionNamePolicy;
    private Integer decisionDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> decisionDefinitionVersionPolicy;
    private String decisionDefinitionType;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionTypePolicy;
    private String output;
    private ContractPolicy.FieldPolicy<String> outputPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object matchedRules;
    private ContractPolicy.FieldPolicy<Object> matchedRulesPolicy;
    private Object evaluatedInputs;
    private ContractPolicy.FieldPolicy<Object> evaluatedInputsPolicy;
    private Object decisionDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> decisionDefinitionKeyPolicy;
    private Object decisionEvaluationInstanceKey;
    private ContractPolicy.FieldPolicy<Object> decisionEvaluationInstanceKeyPolicy;

    private Builder() {}

    @Override
    public DecisionDefinitionNameStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionId = decisionDefinitionId;
      this.decisionDefinitionIdPolicy = policy;
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
    public DecisionDefinitionTypeStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.decisionDefinitionVersion = decisionDefinitionVersion;
      this.decisionDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public OutputStep decisionDefinitionType(
        final String decisionDefinitionType, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionType = decisionDefinitionType;
      this.decisionDefinitionTypePolicy = policy;
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
    public MatchedRulesStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public EvaluatedInputsStep matchedRules(
        final Object matchedRules, final ContractPolicy.FieldPolicy<Object> policy) {
      this.matchedRules = matchedRules;
      this.matchedRulesPolicy = policy;
      return this;
    }

    @Override
    public DecisionDefinitionKeyStep evaluatedInputs(
        final Object evaluatedInputs, final ContractPolicy.FieldPolicy<Object> policy) {
      this.evaluatedInputs = evaluatedInputs;
      this.evaluatedInputsPolicy = policy;
      return this;
    }

    @Override
    public DecisionEvaluationInstanceKeyStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      this.decisionDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationInstanceKey(
        final Object decisionEvaluationInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionEvaluationInstanceKey = decisionEvaluationInstanceKey;
      this.decisionEvaluationInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public GeneratedEvaluatedDecisionStrictContract build() {
      return new GeneratedEvaluatedDecisionStrictContract(
          applyRequiredPolicy(
              this.decisionDefinitionId,
              this.decisionDefinitionIdPolicy,
              Fields.DECISION_DEFINITION_ID),
          applyRequiredPolicy(
              this.decisionDefinitionName,
              this.decisionDefinitionNamePolicy,
              Fields.DECISION_DEFINITION_NAME),
          applyRequiredPolicy(
              this.decisionDefinitionVersion,
              this.decisionDefinitionVersionPolicy,
              Fields.DECISION_DEFINITION_VERSION),
          applyRequiredPolicy(
              this.decisionDefinitionType,
              this.decisionDefinitionTypePolicy,
              Fields.DECISION_DEFINITION_TYPE),
          applyRequiredPolicy(this.output, this.outputPolicy, Fields.OUTPUT),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceMatchedRules(
              applyRequiredPolicy(
                  this.matchedRules, this.matchedRulesPolicy, Fields.MATCHED_RULES)),
          coerceEvaluatedInputs(
              applyRequiredPolicy(
                  this.evaluatedInputs, this.evaluatedInputsPolicy, Fields.EVALUATED_INPUTS)),
          coerceDecisionDefinitionKey(
              applyRequiredPolicy(
                  this.decisionDefinitionKey,
                  this.decisionDefinitionKeyPolicy,
                  Fields.DECISION_DEFINITION_KEY)),
          coerceDecisionEvaluationInstanceKey(
              applyRequiredPolicy(
                  this.decisionEvaluationInstanceKey,
                  this.decisionEvaluationInstanceKeyPolicy,
                  Fields.DECISION_EVALUATION_INSTANCE_KEY)));
    }
  }

  public interface DecisionDefinitionIdStep {
    DecisionDefinitionNameStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionDefinitionNameStep {
    DecisionDefinitionVersionStep decisionDefinitionName(
        final String decisionDefinitionName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionDefinitionVersionStep {
    DecisionDefinitionTypeStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface DecisionDefinitionTypeStep {
    OutputStep decisionDefinitionType(
        final String decisionDefinitionType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OutputStep {
    TenantIdStep output(final String output, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    MatchedRulesStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MatchedRulesStep {
    EvaluatedInputsStep matchedRules(
        final Object matchedRules, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface EvaluatedInputsStep {
    DecisionDefinitionKeyStep evaluatedInputs(
        final Object evaluatedInputs, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionDefinitionKeyStep {
    DecisionEvaluationInstanceKeyStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionEvaluationInstanceKeyStep {
    OptionalStep decisionEvaluationInstanceKey(
        final Object decisionEvaluationInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedEvaluatedDecisionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("EvaluatedDecisionResult", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_NAME =
        ContractPolicy.field("EvaluatedDecisionResult", "decisionDefinitionName");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_VERSION =
        ContractPolicy.field("EvaluatedDecisionResult", "decisionDefinitionVersion");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_TYPE =
        ContractPolicy.field("EvaluatedDecisionResult", "decisionDefinitionType");
    public static final ContractPolicy.FieldRef OUTPUT =
        ContractPolicy.field("EvaluatedDecisionResult", "output");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("EvaluatedDecisionResult", "tenantId");
    public static final ContractPolicy.FieldRef MATCHED_RULES =
        ContractPolicy.field("EvaluatedDecisionResult", "matchedRules");
    public static final ContractPolicy.FieldRef EVALUATED_INPUTS =
        ContractPolicy.field("EvaluatedDecisionResult", "evaluatedInputs");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("EvaluatedDecisionResult", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_INSTANCE_KEY =
        ContractPolicy.field("EvaluatedDecisionResult", "decisionEvaluationInstanceKey");

    private Fields() {}
  }
}
