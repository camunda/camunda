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
import org.jspecify.annotations.NullMarked;

@NullMarked
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

  public static ConditionalEvaluationKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ConditionalEvaluationKeyStep, TenantIdStep, ProcessInstancesStep, OptionalStep {
    private Object conditionalEvaluationKey;
    private String tenantId;
    private Object processInstances;

    private Builder() {}

    @Override
    public TenantIdStep conditionalEvaluationKey(final Object conditionalEvaluationKey) {
      this.conditionalEvaluationKey = conditionalEvaluationKey;
      return this;
    }

    @Override
    public ProcessInstancesStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep processInstances(final Object processInstances) {
      this.processInstances = processInstances;
      return this;
    }

    @Override
    public GeneratedEvaluateConditionalStrictContract build() {
      return new GeneratedEvaluateConditionalStrictContract(
          coerceConditionalEvaluationKey(this.conditionalEvaluationKey),
          this.tenantId,
          coerceProcessInstances(this.processInstances));
    }
  }

  public interface ConditionalEvaluationKeyStep {
    TenantIdStep conditionalEvaluationKey(final Object conditionalEvaluationKey);
  }

  public interface TenantIdStep {
    ProcessInstancesStep tenantId(final String tenantId);
  }

  public interface ProcessInstancesStep {
    OptionalStep processInstances(final Object processInstances);
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
