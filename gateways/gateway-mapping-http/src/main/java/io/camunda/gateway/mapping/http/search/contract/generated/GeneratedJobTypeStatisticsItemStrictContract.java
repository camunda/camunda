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
public record GeneratedJobTypeStatisticsItemStrictContract(
    String jobType,
    GeneratedStatusMetricStrictContract created,
    GeneratedStatusMetricStrictContract completed,
    GeneratedStatusMetricStrictContract failed,
    Integer workers) {

  public GeneratedJobTypeStatisticsItemStrictContract {
    Objects.requireNonNull(jobType, "jobType is required and must not be null");
    Objects.requireNonNull(created, "created is required and must not be null");
    Objects.requireNonNull(completed, "completed is required and must not be null");
    Objects.requireNonNull(failed, "failed is required and must not be null");
    Objects.requireNonNull(workers, "workers is required and must not be null");
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

  public static JobTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements JobTypeStep, CreatedStep, CompletedStep, FailedStep, WorkersStep, OptionalStep {
    private String jobType;
    private ContractPolicy.FieldPolicy<String> jobTypePolicy;
    private Object created;
    private ContractPolicy.FieldPolicy<Object> createdPolicy;
    private Object completed;
    private ContractPolicy.FieldPolicy<Object> completedPolicy;
    private Object failed;
    private ContractPolicy.FieldPolicy<Object> failedPolicy;
    private Integer workers;
    private ContractPolicy.FieldPolicy<Integer> workersPolicy;

    private Builder() {}

    @Override
    public CreatedStep jobType(
        final String jobType, final ContractPolicy.FieldPolicy<String> policy) {
      this.jobType = jobType;
      this.jobTypePolicy = policy;
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
    public WorkersStep failed(
        final Object failed, final ContractPolicy.FieldPolicy<Object> policy) {
      this.failed = failed;
      this.failedPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep workers(
        final Integer workers, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.workers = workers;
      this.workersPolicy = policy;
      return this;
    }

    @Override
    public GeneratedJobTypeStatisticsItemStrictContract build() {
      return new GeneratedJobTypeStatisticsItemStrictContract(
          applyRequiredPolicy(this.jobType, this.jobTypePolicy, Fields.JOB_TYPE),
          coerceCreated(applyRequiredPolicy(this.created, this.createdPolicy, Fields.CREATED)),
          coerceCompleted(
              applyRequiredPolicy(this.completed, this.completedPolicy, Fields.COMPLETED)),
          coerceFailed(applyRequiredPolicy(this.failed, this.failedPolicy, Fields.FAILED)),
          applyRequiredPolicy(this.workers, this.workersPolicy, Fields.WORKERS));
    }
  }

  public interface JobTypeStep {
    CreatedStep jobType(final String jobType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface CreatedStep {
    CompletedStep created(final Object created, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface CompletedStep {
    FailedStep completed(final Object completed, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface FailedStep {
    WorkersStep failed(final Object failed, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface WorkersStep {
    OptionalStep workers(final Integer workers, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OptionalStep {
    GeneratedJobTypeStatisticsItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef JOB_TYPE =
        ContractPolicy.field("JobTypeStatisticsItem", "jobType");
    public static final ContractPolicy.FieldRef CREATED =
        ContractPolicy.field("JobTypeStatisticsItem", "created");
    public static final ContractPolicy.FieldRef COMPLETED =
        ContractPolicy.field("JobTypeStatisticsItem", "completed");
    public static final ContractPolicy.FieldRef FAILED =
        ContractPolicy.field("JobTypeStatisticsItem", "failed");
    public static final ContractPolicy.FieldRef WORKERS =
        ContractPolicy.field("JobTypeStatisticsItem", "workers");

    private Fields() {}
  }
}
