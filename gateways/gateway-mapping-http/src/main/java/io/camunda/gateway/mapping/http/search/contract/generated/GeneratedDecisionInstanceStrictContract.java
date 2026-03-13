/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-instances.yaml#/components/schemas/DecisionInstanceResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
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
    String tenantId
) {

  public GeneratedDecisionInstanceStrictContract {
    Objects.requireNonNull(decisionDefinitionId, "decisionDefinitionId is required and must not be null");
    Objects.requireNonNull(decisionDefinitionKey, "decisionDefinitionKey is required and must not be null");
    Objects.requireNonNull(decisionDefinitionName, "decisionDefinitionName is required and must not be null");
    Objects.requireNonNull(decisionDefinitionType, "decisionDefinitionType is required and must not be null");
    Objects.requireNonNull(decisionDefinitionVersion, "decisionDefinitionVersion is required and must not be null");
    Objects.requireNonNull(decisionEvaluationInstanceKey, "decisionEvaluationInstanceKey is required and must not be null");
    Objects.requireNonNull(decisionEvaluationKey, "decisionEvaluationKey is required and must not be null");
    Objects.requireNonNull(evaluationDate, "evaluationDate is required and must not be null");
    Objects.requireNonNull(result, "result is required and must not be null");
    Objects.requireNonNull(rootDecisionDefinitionKey, "rootDecisionDefinitionKey is required and must not be null");
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
        "decisionEvaluationInstanceKey must be a String or Number, but was " + value.getClass().getName());
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
        "rootDecisionDefinitionKey must be a String or Number, but was " + value.getClass().getName());
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



  public static DecisionDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements DecisionDefinitionIdStep, DecisionDefinitionKeyStep, DecisionDefinitionNameStep, DecisionDefinitionTypeStep, DecisionDefinitionVersionStep, DecisionEvaluationInstanceKeyStep, DecisionEvaluationKeyStep, EvaluationDateStep, ResultStep, RootDecisionDefinitionKeyStep, StateStep, TenantIdStep, OptionalStep {
    private String decisionDefinitionId;
    private Object decisionDefinitionKey;
    private String decisionDefinitionName;
    private io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType;
    private Integer decisionDefinitionVersion;
    private Object decisionEvaluationInstanceKey;
    private Object decisionEvaluationKey;
    private Object elementInstanceKey;
    private String evaluationDate;
    private String evaluationFailure;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private String result;
    private Object rootDecisionDefinitionKey;
    private Object rootProcessInstanceKey;
    private io.camunda.gateway.protocol.model.DecisionInstanceStateEnum state;
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
    public DecisionDefinitionTypeStep decisionDefinitionName(final String decisionDefinitionName) {
      this.decisionDefinitionName = decisionDefinitionName;
      return this;
    }

    @Override
    public DecisionDefinitionVersionStep decisionDefinitionType(final io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType) {
      this.decisionDefinitionType = decisionDefinitionType;
      return this;
    }

    @Override
    public DecisionEvaluationInstanceKeyStep decisionDefinitionVersion(final Integer decisionDefinitionVersion) {
      this.decisionDefinitionVersion = decisionDefinitionVersion;
      return this;
    }

    @Override
    public DecisionEvaluationKeyStep decisionEvaluationInstanceKey(final Object decisionEvaluationInstanceKey) {
      this.decisionEvaluationInstanceKey = decisionEvaluationInstanceKey;
      return this;
    }

    @Override
    public EvaluationDateStep decisionEvaluationKey(final Object decisionEvaluationKey) {
      this.decisionEvaluationKey = decisionEvaluationKey;
      return this;
    }

    @Override
    public ResultStep evaluationDate(final String evaluationDate) {
      this.evaluationDate = evaluationDate;
      return this;
    }

    @Override
    public RootDecisionDefinitionKeyStep result(final String result) {
      this.result = result;
      return this;
    }

    @Override
    public StateStep rootDecisionDefinitionKey(final Object rootDecisionDefinitionKey) {
      this.rootDecisionDefinitionKey = rootDecisionDefinitionKey;
      return this;
    }

    @Override
    public TenantIdStep state(final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
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

    public Builder elementInstanceKey(final @Nullable String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }


    @Override
    public OptionalStep evaluationFailure(final @Nullable String evaluationFailure) {
      this.evaluationFailure = evaluationFailure;
      return this;
    }

    @Override
    public OptionalStep evaluationFailure(final @Nullable String evaluationFailure, final ContractPolicy.FieldPolicy<String> policy) {
      this.evaluationFailure = policy.apply(evaluationFailure, Fields.EVALUATION_FAILURE, null);
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

    public Builder processDefinitionKey(final @Nullable String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey = policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
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

    public Builder processInstanceKey(final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
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

    public Builder rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedDecisionInstanceStrictContract build() {
      return new GeneratedDecisionInstanceStrictContract(
          this.decisionDefinitionId,
          coerceDecisionDefinitionKey(this.decisionDefinitionKey),
          this.decisionDefinitionName,
          this.decisionDefinitionType,
          this.decisionDefinitionVersion,
          coerceDecisionEvaluationInstanceKey(this.decisionEvaluationInstanceKey),
          coerceDecisionEvaluationKey(this.decisionEvaluationKey),
          coerceElementInstanceKey(this.elementInstanceKey),
          this.evaluationDate,
          this.evaluationFailure,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          this.result,
          coerceRootDecisionDefinitionKey(this.rootDecisionDefinitionKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          this.state,
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
    DecisionDefinitionTypeStep decisionDefinitionName(final String decisionDefinitionName);
  }

  public interface DecisionDefinitionTypeStep {
    DecisionDefinitionVersionStep decisionDefinitionType(final io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum decisionDefinitionType);
  }

  public interface DecisionDefinitionVersionStep {
    DecisionEvaluationInstanceKeyStep decisionDefinitionVersion(final Integer decisionDefinitionVersion);
  }

  public interface DecisionEvaluationInstanceKeyStep {
    DecisionEvaluationKeyStep decisionEvaluationInstanceKey(final Object decisionEvaluationInstanceKey);
  }

  public interface DecisionEvaluationKeyStep {
    EvaluationDateStep decisionEvaluationKey(final Object decisionEvaluationKey);
  }

  public interface EvaluationDateStep {
    ResultStep evaluationDate(final String evaluationDate);
  }

  public interface ResultStep {
    RootDecisionDefinitionKeyStep result(final String result);
  }

  public interface RootDecisionDefinitionKeyStep {
    StateStep rootDecisionDefinitionKey(final Object rootDecisionDefinitionKey);
  }

  public interface StateStep {
    TenantIdStep state(final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum state);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId);
  }

  public interface OptionalStep {
  OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey);

  OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

  OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep evaluationFailure(final @Nullable String evaluationFailure);

  OptionalStep evaluationFailure(final @Nullable String evaluationFailure, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey);

  OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

  OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep processInstanceKey(final @Nullable String processInstanceKey);

  OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

  OptionalStep processInstanceKey(final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep processInstanceKey(final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedDecisionInstanceStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID = ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY = ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_NAME = ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionName");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_TYPE = ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionType");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_VERSION = ContractPolicy.field("DecisionInstanceResult", "decisionDefinitionVersion");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_INSTANCE_KEY = ContractPolicy.field("DecisionInstanceResult", "decisionEvaluationInstanceKey");
    public static final ContractPolicy.FieldRef DECISION_EVALUATION_KEY = ContractPolicy.field("DecisionInstanceResult", "decisionEvaluationKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY = ContractPolicy.field("DecisionInstanceResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef EVALUATION_DATE = ContractPolicy.field("DecisionInstanceResult", "evaluationDate");
    public static final ContractPolicy.FieldRef EVALUATION_FAILURE = ContractPolicy.field("DecisionInstanceResult", "evaluationFailure");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY = ContractPolicy.field("DecisionInstanceResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY = ContractPolicy.field("DecisionInstanceResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef RESULT = ContractPolicy.field("DecisionInstanceResult", "result");
    public static final ContractPolicy.FieldRef ROOT_DECISION_DEFINITION_KEY = ContractPolicy.field("DecisionInstanceResult", "rootDecisionDefinitionKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY = ContractPolicy.field("DecisionInstanceResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef STATE = ContractPolicy.field("DecisionInstanceResult", "state");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("DecisionInstanceResult", "tenantId");

    private Fields() {}
  }


}
