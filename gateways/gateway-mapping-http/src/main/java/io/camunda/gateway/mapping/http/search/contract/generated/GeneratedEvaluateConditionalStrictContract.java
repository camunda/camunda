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
public record GeneratedEvaluateConditionalStrictContract(
    String conditionalEvaluationKey,
    String tenantId,
    java.util.List<GeneratedProcessInstanceReferenceStrictContract> processInstances) {

  public GeneratedEvaluateConditionalStrictContract {
    Objects.requireNonNull(
        conditionalEvaluationKey, "conditionalEvaluationKey is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(processInstances, "processInstances is required and must not be null");
  }

  public static String coerceConditionalEvaluationKey(final Object value) {
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
        "conditionalEvaluationKey must be a String or Number, but was "
            + value.getClass().getName());
  }

  public static java.util.List<GeneratedProcessInstanceReferenceStrictContract>
      coerceProcessInstances(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "processInstances must be a List of GeneratedProcessInstanceReferenceStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedProcessInstanceReferenceStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedProcessInstanceReferenceStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "processInstances must contain only GeneratedProcessInstanceReferenceStrictContract items, but got "
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

  public static ConditionalEvaluationKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ConditionalEvaluationKeyStep, TenantIdStep, ProcessInstancesStep, OptionalStep {
    private Object conditionalEvaluationKey;
    private ContractPolicy.FieldPolicy<Object> conditionalEvaluationKeyPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object processInstances;
    private ContractPolicy.FieldPolicy<Object> processInstancesPolicy;

    private Builder() {}

    @Override
    public TenantIdStep conditionalEvaluationKey(
        final Object conditionalEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.conditionalEvaluationKey = conditionalEvaluationKey;
      this.conditionalEvaluationKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstancesStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep processInstances(
        final Object processInstances, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstances = processInstances;
      this.processInstancesPolicy = policy;
      return this;
    }

    @Override
    public GeneratedEvaluateConditionalStrictContract build() {
      return new GeneratedEvaluateConditionalStrictContract(
          coerceConditionalEvaluationKey(
              applyRequiredPolicy(
                  this.conditionalEvaluationKey,
                  this.conditionalEvaluationKeyPolicy,
                  Fields.CONDITIONAL_EVALUATION_KEY)),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceProcessInstances(
              applyRequiredPolicy(
                  this.processInstances, this.processInstancesPolicy, Fields.PROCESS_INSTANCES)));
    }
  }

  public interface ConditionalEvaluationKeyStep {
    TenantIdStep conditionalEvaluationKey(
        final Object conditionalEvaluationKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface TenantIdStep {
    ProcessInstancesStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessInstancesStep {
    OptionalStep processInstances(
        final Object processInstances, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedEvaluateConditionalStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CONDITIONAL_EVALUATION_KEY =
        ContractPolicy.field("EvaluateConditionalResult", "conditionalEvaluationKey");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("EvaluateConditionalResult", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCES =
        ContractPolicy.field("EvaluateConditionalResult", "processInstances");

    private Fields() {}
  }
}
