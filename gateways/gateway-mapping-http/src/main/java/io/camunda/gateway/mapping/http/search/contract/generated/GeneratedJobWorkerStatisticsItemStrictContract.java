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
import org.jspecify.annotations.NullMarked;

@NullMarked
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

  public static WorkerStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements WorkerStep, CreatedStep, CompletedStep, FailedStep, OptionalStep {
    private String worker;
    private Object created;
    private Object completed;
    private Object failed;

    private Builder() {}

    @Override
    public CreatedStep worker(final String worker) {
      this.worker = worker;
      return this;
    }

    @Override
    public CompletedStep created(final Object created) {
      this.created = created;
      return this;
    }

    @Override
    public FailedStep completed(final Object completed) {
      this.completed = completed;
      return this;
    }

    @Override
    public OptionalStep failed(final Object failed) {
      this.failed = failed;
      return this;
    }

    @Override
    public GeneratedJobWorkerStatisticsItemStrictContract build() {
      return new GeneratedJobWorkerStatisticsItemStrictContract(
          this.worker,
          coerceCreated(this.created),
          coerceCompleted(this.completed),
          coerceFailed(this.failed));
    }
  }

  public interface WorkerStep {
    CreatedStep worker(final String worker);
  }

  public interface CreatedStep {
    CompletedStep created(final Object created);
  }

  public interface CompletedStep {
    FailedStep completed(final Object completed);
  }

  public interface FailedStep {
    OptionalStep failed(final Object failed);
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
