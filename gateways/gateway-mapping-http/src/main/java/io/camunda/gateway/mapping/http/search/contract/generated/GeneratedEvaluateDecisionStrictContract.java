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
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedEvaluateDecisionStrictContract(
    @JsonProperty("decisionDefinitionId") String decisionDefinitionId,
    @JsonProperty("decisionDefinitionKey") String decisionDefinitionKey,
    @JsonProperty("decisionDefinitionName") String decisionDefinitionName,
    @JsonProperty("decisionDefinitionVersion") Integer decisionDefinitionVersion,
    @JsonProperty("decisionEvaluationKey") String decisionEvaluationKey,
    @JsonProperty("decisionInstanceKey") String decisionInstanceKey,
    @JsonProperty("decisionRequirementsId") String decisionRequirementsId,
    @JsonProperty("decisionRequirementsKey") String decisionRequirementsKey,
    @JsonProperty("evaluatedDecisions")
        java.util.List<GeneratedEvaluatedDecisionStrictContract> evaluatedDecisions,
    @JsonProperty("failedDecisionDefinitionId") @Nullable String failedDecisionDefinitionId,
    @JsonProperty("failureMessage") @Nullable String failureMessage,
    @JsonProperty("output") String output,
    @JsonProperty("tenantId") String tenantId) {

  public GeneratedEvaluateDecisionStrictContract {
    Objects.requireNonNull(decisionDefinitionId, "No decisionDefinitionId provided.");
    Objects.requireNonNull(decisionDefinitionKey, "No decisionDefinitionKey provided.");
    Objects.requireNonNull(decisionDefinitionName, "No decisionDefinitionName provided.");
    Objects.requireNonNull(decisionDefinitionVersion, "No decisionDefinitionVersion provided.");
    Objects.requireNonNull(decisionEvaluationKey, "No decisionEvaluationKey provided.");
    Objects.requireNonNull(decisionInstanceKey, "No decisionInstanceKey provided.");
    Objects.requireNonNull(decisionRequirementsId, "No decisionRequirementsId provided.");
    Objects.requireNonNull(decisionRequirementsKey, "No decisionRequirementsKey provided.");
    Objects.requireNonNull(evaluatedDecisions, "No evaluatedDecisions provided.");
    Objects.requireNonNull(output, "No output provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
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
    private Object decisionDefinitionKey;
    private String decisionDefinitionName;
    private Integer decisionDefinitionVersion;
    private Object decisionEvaluationKey;
    private Object decisionInstanceKey;
    private String decisionRequirementsId;
    private Object decisionRequirementsKey;
    private Object evaluatedDecisions;
    private String failedDecisionDefinitionId;
    private String failureMessage;
    private String output;
    private String tenantId;

    private Builder() {}

    @Override
    public DecisionDefinitionKeyStep decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    @Override
    public DecisionDefinitionNameStep decisionDefinitionKey(final Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    @Override
    public DecisionDefinitionVersionStep decisionDefinitionName(
        final String decisionDefinitionName) {
      this.decisionDefinitionName = decisionDefinitionName;
      return this;
    }

    @Override
    public DecisionEvaluationKeyStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion) {
      this.decisionDefinitionVersion = decisionDefinitionVersion;
      return this;
    }

    @Override
    public DecisionInstanceKeyStep decisionEvaluationKey(final Object decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    @Override
    public DecisionRequirementsIdStep decisionInstanceKey(final Object decisionInstanceKey) {
      this.decisionInstanceKey = decisionInstanceKey;
      return this;
    }

    @Override
    public DecisionRequirementsKeyStep decisionRequirementsId(final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public EvaluatedDecisionsStep decisionRequirementsKey(final Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public OutputStep evaluatedDecisions(final Object evaluatedDecisions) {
      this.evaluatedDecisions = evaluatedDecisions;
      return this;
    }

    @Override
    public TenantIdStep output(final String output) {
      this.output = output;
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep failedDecisionDefinitionId(
        final @Nullable String failedDecisionDefinitionId) {
      this.failedDecisionDefinitionId = failedDecisionDefinitionId;
      return this;
    }

    @Override
    public OptionalStep failedDecisionDefinitionId(
        final @Nullable String failedDecisionDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.failedDecisionDefinitionId =
          policy.apply(failedDecisionDefinitionId, Fields.FAILED_DECISION_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep failureMessage(final @Nullable String failureMessage) {
      this.failureMessage = failureMessage;
      return this;
    }

    @Override
    public OptionalStep failureMessage(
        final @Nullable String failureMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.failureMessage = policy.apply(failureMessage, Fields.FAILURE_MESSAGE, null);
      return this;
    }

    @Override
    public GeneratedEvaluateDecisionStrictContract build() {
      return new GeneratedEvaluateDecisionStrictContract(
          this.decisionDefinitionId,
          coerceDecisionDefinitionKey(this.decisionDefinitionKey),
          this.decisionDefinitionName,
          this.decisionDefinitionVersion,
          coerceDecisionEvaluationKey(this.decisionEvaluationKey),
          coerceDecisionInstanceKey(this.decisionInstanceKey),
          this.decisionRequirementsId,
          coerceDecisionRequirementsKey(this.decisionRequirementsKey),
          coerceEvaluatedDecisions(this.evaluatedDecisions),
          this.failedDecisionDefinitionId,
          this.failureMessage,
          this.output,
          this.tenantId);
    }
  }

  public interface DecisionDefinitionIdStep {
    DecisionDefinitionKeyStep decisionDefinitionId(final String decisionDefinitionId);
  }

  public interface DecisionDefinitionKeyStep {
    DecisionDefinitionNameStep decisionDefinitionKey(final Object decisionDefinitionKey);
  }

  public interface DecisionDefinitionNameStep {
    DecisionDefinitionVersionStep decisionDefinitionName(final String decisionDefinitionName);
  }

  public interface DecisionDefinitionVersionStep {
    DecisionEvaluationKeyStep decisionDefinitionVersion(final Integer decisionDefinitionVersion);
  }

  public interface DecisionEvaluationKeyStep {
    DecisionInstanceKeyStep decisionEvaluationKey(final Object decisionEvaluationKey);
  }

  public interface DecisionInstanceKeyStep {
    DecisionRequirementsIdStep decisionInstanceKey(final Object decisionInstanceKey);
  }

  public interface DecisionRequirementsIdStep {
    DecisionRequirementsKeyStep decisionRequirementsId(final String decisionRequirementsId);
  }

  public interface DecisionRequirementsKeyStep {
    EvaluatedDecisionsStep decisionRequirementsKey(final Object decisionRequirementsKey);
  }

  public interface EvaluatedDecisionsStep {
    OutputStep evaluatedDecisions(final Object evaluatedDecisions);
  }

  public interface OutputStep {
    TenantIdStep output(final String output);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId);
  }

  public interface OptionalStep {
    OptionalStep failedDecisionDefinitionId(final @Nullable String failedDecisionDefinitionId);

    OptionalStep failedDecisionDefinitionId(
        final @Nullable String failedDecisionDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep failureMessage(final @Nullable String failureMessage);

    OptionalStep failureMessage(
        final @Nullable String failureMessage, final ContractPolicy.FieldPolicy<String> policy);

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
