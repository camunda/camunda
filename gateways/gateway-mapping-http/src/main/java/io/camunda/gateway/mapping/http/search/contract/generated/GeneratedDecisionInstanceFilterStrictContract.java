/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-instances.yaml#/components/schemas/DecisionInstanceFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionInstanceFilterStrictContract(
    @JsonProperty("decisionEvaluationInstanceKey")
        @Nullable GeneratedDecisionEvaluationInstanceKeyFilterPropertyStrictContract
            decisionEvaluationInstanceKey,
    @JsonProperty("state")
        @Nullable GeneratedDecisionInstanceStateFilterPropertyStrictContract state,
    @JsonProperty("evaluationFailure") @Nullable String evaluationFailure,
    @JsonProperty("evaluationDate")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract evaluationDate,
    @JsonProperty("decisionDefinitionId") @Nullable String decisionDefinitionId,
    @JsonProperty("decisionDefinitionName") @Nullable String decisionDefinitionName,
    @JsonProperty("decisionDefinitionVersion") @Nullable Integer decisionDefinitionVersion,
    @JsonProperty("decisionDefinitionType")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionDefinitionTypeEnum
            decisionDefinitionType,
    @JsonProperty("tenantId") @Nullable String tenantId,
    @JsonProperty("decisionEvaluationKey") @Nullable String decisionEvaluationKey,
    @JsonProperty("processDefinitionKey") @Nullable String processDefinitionKey,
    @JsonProperty("processInstanceKey") @Nullable String processInstanceKey,
    @JsonProperty("decisionDefinitionKey")
        @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract decisionDefinitionKey,
    @JsonProperty("elementInstanceKey")
        @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
    @JsonProperty("rootDecisionDefinitionKey")
        @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            rootDecisionDefinitionKey,
    @JsonProperty("decisionRequirementsKey")
        @Nullable GeneratedDecisionRequirementsKeyFilterPropertyStrictContract
            decisionRequirementsKey) {

  public GeneratedDecisionInstanceFilterStrictContract {
    if (decisionDefinitionId != null)
      if (decisionDefinitionId.isBlank())
        throw new IllegalArgumentException("decisionDefinitionId must not be blank");
    if (decisionDefinitionId != null)
      if (decisionDefinitionId.length() > 256)
        throw new IllegalArgumentException(
            "The provided decisionDefinitionId exceeds the limit of 256 characters.");
    if (decisionDefinitionId != null)
      if (!decisionDefinitionId.matches("^[A-Za-z0-9_@.+-]+$"))
        throw new IllegalArgumentException(
            "The provided decisionDefinitionId contains illegal characters. It must match the pattern '^[A-Za-z0-9_@.+-]+$'.");
    if (tenantId != null)
      if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    if (tenantId != null)
      if (tenantId.length() > 256)
        throw new IllegalArgumentException(
            "The provided tenantId exceeds the limit of 256 characters.");
    if (tenantId != null)
      if (!tenantId.matches("^(<default>|[A-Za-z0-9_@.+-]+)$"))
        throw new IllegalArgumentException(
            "The provided tenantId contains illegal characters. It must match the pattern '^(<default>|[A-Za-z0-9_@.+-]+)$'.");
    if (decisionEvaluationKey != null)
      if (decisionEvaluationKey.isBlank())
        throw new IllegalArgumentException("decisionEvaluationKey must not be blank");
    if (decisionEvaluationKey != null)
      if (decisionEvaluationKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided decisionEvaluationKey exceeds the limit of 25 characters.");
    if (decisionEvaluationKey != null)
      if (!decisionEvaluationKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided decisionEvaluationKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (processDefinitionKey != null)
      if (processDefinitionKey.isBlank())
        throw new IllegalArgumentException("processDefinitionKey must not be blank");
    if (processDefinitionKey != null)
      if (processDefinitionKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided processDefinitionKey exceeds the limit of 25 characters.");
    if (processDefinitionKey != null)
      if (!processDefinitionKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided processDefinitionKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (processInstanceKey != null)
      if (processInstanceKey.isBlank())
        throw new IllegalArgumentException("processInstanceKey must not be blank");
    if (processInstanceKey != null)
      if (processInstanceKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided processInstanceKey exceeds the limit of 25 characters.");
    if (processInstanceKey != null)
      if (!processInstanceKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided processInstanceKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
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

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedDecisionEvaluationInstanceKeyFilterPropertyStrictContract
        decisionEvaluationInstanceKey;
    private GeneratedDecisionInstanceStateFilterPropertyStrictContract state;
    private String evaluationFailure;
    private GeneratedDateTimeFilterPropertyStrictContract evaluationDate;
    private String decisionDefinitionId;
    private String decisionDefinitionName;
    private Integer decisionDefinitionVersion;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedDecisionDefinitionTypeEnum
        decisionDefinitionType;
    private String tenantId;
    private Object decisionEvaluationKey;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private GeneratedDecisionDefinitionKeyFilterPropertyStrictContract decisionDefinitionKey;
    private GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey;
    private GeneratedDecisionDefinitionKeyFilterPropertyStrictContract rootDecisionDefinitionKey;
    private GeneratedDecisionRequirementsKeyFilterPropertyStrictContract decisionRequirementsKey;

    private Builder() {}

    @Override
    public OptionalStep decisionEvaluationInstanceKey(
        final @Nullable GeneratedDecisionEvaluationInstanceKeyFilterPropertyStrictContract
            decisionEvaluationInstanceKey) {
      this.decisionEvaluationInstanceKey = decisionEvaluationInstanceKey;
      return this;
    }

    @Override
    public OptionalStep decisionEvaluationInstanceKey(
        final @Nullable GeneratedDecisionEvaluationInstanceKeyFilterPropertyStrictContract
            decisionEvaluationInstanceKey,
        final ContractPolicy.FieldPolicy<
                GeneratedDecisionEvaluationInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.decisionEvaluationInstanceKey =
          policy.apply(
              decisionEvaluationInstanceKey, Fields.DECISION_EVALUATION_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedDecisionInstanceStateFilterPropertyStrictContract state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedDecisionInstanceStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedDecisionInstanceStateFilterPropertyStrictContract>
            policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep evaluationFailure(final @Nullable String evaluationFailure) {
      this.evaluationFailure = evaluationFailure;
      return this;
    }

    @Override
    public OptionalStep evaluationFailure(
        final @Nullable String evaluationFailure, final ContractPolicy.FieldPolicy<String> policy) {
      this.evaluationFailure = policy.apply(evaluationFailure, Fields.EVALUATION_FAILURE, null);
      return this;
    }

    @Override
    public OptionalStep evaluationDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract evaluationDate) {
      this.evaluationDate = evaluationDate;
      return this;
    }

    @Override
    public OptionalStep evaluationDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract evaluationDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.evaluationDate = policy.apply(evaluationDate, Fields.EVALUATION_DATE, null);
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
    public OptionalStep decisionDefinitionName(final @Nullable String decisionDefinitionName) {
      this.decisionDefinitionName = decisionDefinitionName;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionName(
        final @Nullable String decisionDefinitionName,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionName =
          policy.apply(decisionDefinitionName, Fields.DECISION_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionVersion(
        final @Nullable Integer decisionDefinitionVersion) {
      this.decisionDefinitionVersion = decisionDefinitionVersion;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionVersion(
        final @Nullable Integer decisionDefinitionVersion,
        final ContractPolicy.FieldPolicy<Integer> policy) {
      this.decisionDefinitionVersion =
          policy.apply(decisionDefinitionVersion, Fields.DECISION_DEFINITION_VERSION, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionDefinitionTypeEnum
            decisionDefinitionType) {
      this.decisionDefinitionType = decisionDefinitionType;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionDefinitionTypeEnum
            decisionDefinitionType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionDefinitionTypeEnum>
            policy) {
      this.decisionDefinitionType =
          policy.apply(decisionDefinitionType, Fields.DECISION_DEFINITION_TYPE, null);
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
    public OptionalStep rootDecisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            rootDecisionDefinitionKey) {
      this.rootDecisionDefinitionKey = rootDecisionDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep rootDecisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            rootDecisionDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedDecisionDefinitionKeyFilterPropertyStrictContract>
            policy) {
      this.rootDecisionDefinitionKey =
          policy.apply(rootDecisionDefinitionKey, Fields.ROOT_DECISION_DEFINITION_KEY, null);
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
    OptionalStep decisionEvaluationInstanceKey(
        final @Nullable GeneratedDecisionEvaluationInstanceKeyFilterPropertyStrictContract
            decisionEvaluationInstanceKey);

    OptionalStep decisionEvaluationInstanceKey(
        final @Nullable GeneratedDecisionEvaluationInstanceKeyFilterPropertyStrictContract
            decisionEvaluationInstanceKey,
        final ContractPolicy.FieldPolicy<
                GeneratedDecisionEvaluationInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep state(
        final @Nullable GeneratedDecisionInstanceStateFilterPropertyStrictContract state);

    OptionalStep state(
        final @Nullable GeneratedDecisionInstanceStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedDecisionInstanceStateFilterPropertyStrictContract>
            policy);

    OptionalStep evaluationFailure(final @Nullable String evaluationFailure);

    OptionalStep evaluationFailure(
        final @Nullable String evaluationFailure, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep evaluationDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract evaluationDate);

    OptionalStep evaluationDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract evaluationDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep decisionDefinitionId(final @Nullable String decisionDefinitionId);

    OptionalStep decisionDefinitionId(
        final @Nullable String decisionDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionName(final @Nullable String decisionDefinitionName);

    OptionalStep decisionDefinitionName(
        final @Nullable String decisionDefinitionName,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionVersion(final @Nullable Integer decisionDefinitionVersion);

    OptionalStep decisionDefinitionVersion(
        final @Nullable Integer decisionDefinitionVersion,
        final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep decisionDefinitionType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionDefinitionTypeEnum
            decisionDefinitionType);

    OptionalStep decisionDefinitionType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionDefinitionTypeEnum
            decisionDefinitionType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionDefinitionTypeEnum>
            policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionEvaluationKey(final @Nullable String decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(final @Nullable Object decisionEvaluationKey);

    OptionalStep decisionEvaluationKey(
        final @Nullable String decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionEvaluationKey(
        final @Nullable Object decisionEvaluationKey,
        final ContractPolicy.FieldPolicy<Object> policy);

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

    OptionalStep decisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            decisionDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedDecisionDefinitionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep rootDecisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            rootDecisionDefinitionKey);

    OptionalStep rootDecisionDefinitionKey(
        final @Nullable GeneratedDecisionDefinitionKeyFilterPropertyStrictContract
            rootDecisionDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedDecisionDefinitionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep decisionRequirementsKey(
        final @Nullable GeneratedDecisionRequirementsKeyFilterPropertyStrictContract
            decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final @Nullable GeneratedDecisionRequirementsKeyFilterPropertyStrictContract
            decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<
                GeneratedDecisionRequirementsKeyFilterPropertyStrictContract>
            policy);

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
