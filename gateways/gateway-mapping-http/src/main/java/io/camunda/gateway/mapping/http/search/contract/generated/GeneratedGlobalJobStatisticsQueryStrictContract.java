/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGlobalJobStatisticsQueryStrictContract(
    GeneratedStatusMetricStrictContract created,
    GeneratedStatusMetricStrictContract completed,
    GeneratedStatusMetricStrictContract failed,
    Boolean isIncomplete) {

  public GeneratedGlobalJobStatisticsQueryStrictContract {
    Objects.requireNonNull(created, "created is required and must not be null");
    Objects.requireNonNull(completed, "completed is required and must not be null");
    Objects.requireNonNull(failed, "failed is required and must not be null");
    Objects.requireNonNull(isIncomplete, "isIncomplete is required and must not be null");
  }

  public static GeneratedStatusMetricStrictContract coerceCreated(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedStatusMetricStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "created must be a GeneratedStatusMetricStrictContract, but was "
            + value.getClass().getName());
  }

  public static GeneratedStatusMetricStrictContract coerceCompleted(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedStatusMetricStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "completed must be a GeneratedStatusMetricStrictContract, but was "
            + value.getClass().getName());
  }

  public static GeneratedStatusMetricStrictContract coerceFailed(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedStatusMetricStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "failed must be a GeneratedStatusMetricStrictContract, but was "
            + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static CreatedStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements CreatedStep, CompletedStep, FailedStep, IsIncompleteStep, OptionalStep {
    private Object created;
    private ContractPolicy.FieldPolicy<Object> createdPolicy;
    private Object completed;
    private ContractPolicy.FieldPolicy<Object> completedPolicy;
    private Object failed;
    private ContractPolicy.FieldPolicy<Object> failedPolicy;
    private Boolean isIncomplete;
    private ContractPolicy.FieldPolicy<Boolean> isIncompletePolicy;

    private Builder() {}

    @Override
    public CompletedStep created(
        final Object created, final ContractPolicy.FieldPolicy<Object> policy) {
      this.created = created;
      this.createdPolicy = policy;
      return this;
    }

    @Override
    public FailedStep completed(
        final Object completed, final ContractPolicy.FieldPolicy<Object> policy) {
      this.completed = completed;
      this.completedPolicy = policy;
      return this;
    }

    @Override
    public IsIncompleteStep failed(
        final Object failed, final ContractPolicy.FieldPolicy<Object> policy) {
      this.failed = failed;
      this.failedPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep isIncomplete(
        final Boolean isIncomplete, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isIncomplete = isIncomplete;
      this.isIncompletePolicy = policy;
      return this;
    }

    @Override
    public GeneratedGlobalJobStatisticsQueryStrictContract build() {
      return new GeneratedGlobalJobStatisticsQueryStrictContract(
          coerceCreated(applyRequiredPolicy(this.created, this.createdPolicy, Fields.CREATED)),
          coerceCompleted(
              applyRequiredPolicy(this.completed, this.completedPolicy, Fields.COMPLETED)),
          coerceFailed(applyRequiredPolicy(this.failed, this.failedPolicy, Fields.FAILED)),
          applyRequiredPolicy(this.isIncomplete, this.isIncompletePolicy, Fields.IS_INCOMPLETE));
    }
  }

  public interface CreatedStep {
    CompletedStep created(final Object created, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface CompletedStep {
    FailedStep completed(final Object completed, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface FailedStep {
    IsIncompleteStep failed(final Object failed, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface IsIncompleteStep {
    OptionalStep isIncomplete(
        final Boolean isIncomplete, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface OptionalStep {
    GeneratedGlobalJobStatisticsQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CREATED =
        ContractPolicy.field("GlobalJobStatisticsQueryResult", "created");
    public static final ContractPolicy.FieldRef COMPLETED =
        ContractPolicy.field("GlobalJobStatisticsQueryResult", "completed");
    public static final ContractPolicy.FieldRef FAILED =
        ContractPolicy.field("GlobalJobStatisticsQueryResult", "failed");
    public static final ContractPolicy.FieldRef IS_INCOMPLETE =
        ContractPolicy.field("GlobalJobStatisticsQueryResult", "isIncomplete");

    private Fields() {}
  }
}
