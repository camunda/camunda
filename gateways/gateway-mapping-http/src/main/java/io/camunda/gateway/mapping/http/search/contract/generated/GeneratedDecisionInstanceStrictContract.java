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
public record GeneratedDecisionInstanceStrictContract(
    String decisionDefinitionId,
    String decisionDefinitionKey,
    String decisionDefinitionName,
    io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType,
    Integer decisionDefinitionVersion,
    String decisionEvaluationInstanceKey,
    String decisionEvaluationKey,
    @Nullable String elementInstanceKey,
    String evaluationDate,
    @Nullable String evaluationFailure,
    @Nullable String processDefinitionKey,
    @Nullable String processInstanceKey,
    String result,
    String rootDecisionDefinitionKey,
    @Nullable String rootProcessInstanceKey,
    io.camunda.gateway.protocol.model.DecisionInstanceStateEnum state,
    String tenantId) {

  public GeneratedDecisionInstanceStrictContract {
    Objects.requireNonNull(
        decisionDefinitionId, "decisionDefinitionId is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionKey, "decisionDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionName, "decisionDefinitionName is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionType, "decisionDefinitionType is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionVersion, "decisionDefinitionVersion is required and must not be null");
    Objects.requireNonNull(
        decisionEvaluationInstanceKey,
        "decisionEvaluationInstanceKey is required and must not be null");
    Objects.requireNonNull(
        decisionEvaluationKey, "decisionEvaluationKey is required and must not be null");
    Objects.requireNonNull(evaluationDate, "evaluationDate is required and must not be null");
    Objects.requireNonNull(result, "result is required and must not be null");
    Objects.requireNonNull(
        rootDecisionDefinitionKey, "rootDecisionDefinitionKey is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
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

  public static String coerceRootDecisionDefinitionKey(final Object value) {
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
        "rootDecisionDefinitionKey must be a String or Number, but was "
            + value.getClass().getName());
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
          DecisionDefinitionTypeStep,
          DecisionDefinitionVersionStep,
          DecisionEvaluationInstanceKeyStep,
          DecisionEvaluationKeyStep,
          EvaluationDateStep,
          ResultStep,
          RootDecisionDefinitionKeyStep,
          StateStep,
          TenantIdStep,
          OptionalStep {
    private String decisionDefinitionId;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionIdPolicy;
    private Object decisionDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> decisionDefinitionKeyPolicy;
    private String decisionDefinitionName;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionNamePolicy;
    private io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum>
        decisionDefinitionTypePolicy;
    private Integer decisionDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> decisionDefinitionVersionPolicy;
    private Object decisionEvaluationInstanceKey;
    private ContractPolicy.FieldPolicy<Object> decisionEvaluationInstanceKeyPolicy;
    private Object decisionEvaluationKey;
    private ContractPolicy.FieldPolicy<Object> decisionEvaluationKeyPolicy;
    private Object elementInstanceKey;
    private String evaluationDate;
    private ContractPolicy.FieldPolicy<String> evaluationDatePolicy;
    private String evaluationFailure;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private String result;
    private ContractPolicy.FieldPolicy<String> resultPolicy;
    private Object rootDecisionDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> rootDecisionDefinitionKeyPolicy;
    private Object rootProcessInstanceKey;
    private io.camunda.gateway.protocol.model.DecisionInstanceStateEnum state;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>
        statePolicy;
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
    public DecisionDefinitionTypeStep decisionDefinitionName(
        final String decisionDefinitionName, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionName = decisionDefinitionName;
      this.decisionDefinitionNamePolicy = policy;
      return this;
    }

    @Override
    public DecisionDefinitionVersionStep decisionDefinitionType(
        final io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum>
            policy) {
      this.decisionDefinitionType = decisionDefinitionType;
      this.decisionDefinitionTypePolicy = policy;
      return this;
    }

    @Override
    public DecisionEvaluationInstanceKeyStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.decisionDefinitionVersion = decisionDefinitionVersion;
      this.decisionDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public DecisionEvaluationKeyStep decisionEvaluationInstanceKey(
        final Object decisionEvaluationInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionEvaluationInstanceKey = decisionEvaluationInstanceKey;
      this.decisionEvaluationInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public EvaluationDateStep decisionEvaluationKey(
        final Object decisionEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      this.decisionEvaluationKeyPolicy = policy;
      return this;
    }

    @Override
    public ResultStep evaluationDate(
        final String evaluationDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.evaluationDate = evaluationDate;
      this.evaluationDatePolicy = policy;
      return this;
    }

    @Override
    public RootDecisionDefinitionKeyStep result(
        final String result, final ContractPolicy.FieldPolicy<String> policy) {
      this.result = result;
      this.resultPolicy = policy;
      return this;
    }

    @Override
    public StateStep rootDecisionDefinitionKey(
        final Object rootDecisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootDecisionDefinitionKey = rootDecisionDefinitionKey;
      this.rootDecisionDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep state(
        final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum state,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>
            policy) {
      this.state = state;
      this.statePolicy = policy;
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
    public GeneratedDecisionInstanceStrictContract build() {
      return new GeneratedDecisionInstanceStrictContract(
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
              this.decisionDefinitionType,
              this.decisionDefinitionTypePolicy,
              Fields.DECISION_DEFINITION_TYPE),
          applyRequiredPolicy(
              this.decisionDefinitionVersion,
              this.decisionDefinitionVersionPolicy,
              Fields.DECISION_DEFINITION_VERSION),
          coerceDecisionEvaluationInstanceKey(
              applyRequiredPolicy(
                  this.decisionEvaluationInstanceKey,
                  this.decisionEvaluationInstanceKeyPolicy,
                  Fields.DECISION_EVALUATION_INSTANCE_KEY)),
          coerceDecisionEvaluationKey(
              applyRequiredPolicy(
                  this.decisionEvaluationKey,
                  this.decisionEvaluationKeyPolicy,
                  Fields.DECISION_EVALUATION_KEY)),
          coerceElementInstanceKey(this.elementInstanceKey),
          applyRequiredPolicy(
              this.evaluationDate, this.evaluationDatePolicy, Fields.EVALUATION_DATE),
          this.evaluationFailure,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          applyRequiredPolicy(this.result, this.resultPolicy, Fields.RESULT),
          coerceRootDecisionDefinitionKey(
              applyRequiredPolicy(
                  this.rootDecisionDefinitionKey,
                  this.rootDecisionDefinitionKeyPolicy,
                  Fields.ROOT_DECISION_DEFINITION_KEY)),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          applyRequiredPolicy(this.state, this.statePolicy, Fields.STATE),
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
    DecisionDefinitionTypeStep decisionDefinitionName(
        final String decisionDefinitionName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionDefinitionTypeStep {
    DecisionDefinitionVersionStep decisionDefinitionType(
        final io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum>
            policy);
  }

  public interface DecisionDefinitionVersionStep {
    DecisionEvaluationInstanceKeyStep decisionDefinitionVersion(
        final Integer decisionDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface DecisionEvaluationInstanceKeyStep {
    DecisionEvaluationKeyStep decisionEvaluationInstanceKey(
        final Object decisionEvaluationInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionEvaluationKeyStep {
    EvaluationDateStep decisionEvaluationKey(
        final Object decisionEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface EvaluationDateStep {
    ResultStep evaluationDate(
        final String evaluationDate, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ResultStep {
    RootDecisionDefinitionKeyStep result(
        final String result, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface RootDecisionDefinitionKeyStep {
    StateStep rootDecisionDefinitionKey(
        final Object rootDecisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface StateStep {
    TenantIdStep state(
        final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum state,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>
            policy);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep elementInstanceKey(final String elementInstanceKey);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep evaluationFailure(final String evaluationFailure);

    OptionalStep evaluationFailure(
        final String evaluationFailure, final ContractPolicy.FieldPolicy<String> policy);

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

    GeneratedDecisionInstanceStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_NAME =
        ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionName");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_TYPE =
        ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionType");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_VERSION =
        ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionVersion");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_INSTANCE_KEY =
        ContractPolicy.field("DecisionInstanceResult", "decisionEvaluationInstanceKey");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_KEY =
        ContractPolicy.field("DecisionInstanceResult", "decisionEvaluationKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("DecisionInstanceResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef EVALUATION_DATE =
        ContractPolicy.field("DecisionInstanceResult", "evaluationDate");
    public static final ContractPolicy.FieldRef EVALUATION_FAILURE =
        ContractPolicy.field("DecisionInstanceResult", "evaluationFailure");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("DecisionInstanceResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("DecisionInstanceResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef RESULT =
        ContractPolicy.field("DecisionInstanceResult", "result");
    public static final ContractPolicy.FieldRef ROOT_DECISION_DEFINITION_KEY =
        ContractPolicy.field("DecisionInstanceResult", "rootDecisionDefinitionKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("DecisionInstanceResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("DecisionInstanceResult", "state");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DecisionInstanceResult", "tenantId");

    private Fields() {}
  }
}
