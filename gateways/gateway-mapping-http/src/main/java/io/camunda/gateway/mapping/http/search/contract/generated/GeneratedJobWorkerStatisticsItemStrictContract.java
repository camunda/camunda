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
public record GeneratedJobWorkerStatisticsItemStrictContract(
    String worker,
    GeneratedStatusMetricStrictContract created,
    GeneratedStatusMetricStrictContract completed,
    GeneratedStatusMetricStrictContract failed) {

  public GeneratedJobWorkerStatisticsItemStrictContract {
    Objects.requireNonNull(worker, "worker is required and must not be null");
    Objects.requireNonNull(created, "created is required and must not be null");
    Objects.requireNonNull(completed, "completed is required and must not be null");
    Objects.requireNonNull(failed, "failed is required and must not be null");
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

  public static WorkerStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements WorkerStep, CreatedStep, CompletedStep, FailedStep, OptionalStep {
    private String worker;
    private ContractPolicy.FieldPolicy<String> workerPolicy;
    private Object created;
    private ContractPolicy.FieldPolicy<Object> createdPolicy;
    private Object completed;
    private ContractPolicy.FieldPolicy<Object> completedPolicy;
    private Object failed;
    private ContractPolicy.FieldPolicy<Object> failedPolicy;

    private Builder() {}

    @Override
    public CreatedStep worker(
        final String worker, final ContractPolicy.FieldPolicy<String> policy) {
      this.worker = worker;
      this.workerPolicy = policy;
      return this;
    }

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
    public OptionalStep failed(
        final Object failed, final ContractPolicy.FieldPolicy<Object> policy) {
      this.failed = failed;
      this.failedPolicy = policy;
      return this;
    }

    @Override
    public GeneratedJobWorkerStatisticsItemStrictContract build() {
      return new GeneratedJobWorkerStatisticsItemStrictContract(
          applyRequiredPolicy(this.worker, this.workerPolicy, Fields.WORKER),
          coerceCreated(applyRequiredPolicy(this.created, this.createdPolicy, Fields.CREATED)),
          coerceCompleted(
              applyRequiredPolicy(this.completed, this.completedPolicy, Fields.COMPLETED)),
          coerceFailed(applyRequiredPolicy(this.failed, this.failedPolicy, Fields.FAILED)));
    }
  }

  public interface WorkerStep {
    CreatedStep worker(final String worker, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface CreatedStep {
    CompletedStep created(final Object created, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface CompletedStep {
    FailedStep completed(final Object completed, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface FailedStep {
    OptionalStep failed(final Object failed, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedJobWorkerStatisticsItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef WORKER =
        ContractPolicy.field("JobWorkerStatisticsItem", "worker");
    public static final ContractPolicy.FieldRef CREATED =
        ContractPolicy.field("JobWorkerStatisticsItem", "created");
    public static final ContractPolicy.FieldRef COMPLETED =
        ContractPolicy.field("JobWorkerStatisticsItem", "completed");
    public static final ContractPolicy.FieldRef FAILED =
        ContractPolicy.field("JobWorkerStatisticsItem", "failed");

    private Fields() {}
  }
}
