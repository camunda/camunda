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
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobTypeStatisticsItemStrictContract(
    @JsonProperty("jobType") String jobType,
    @JsonProperty("created") GeneratedStatusMetricStrictContract created,
    @JsonProperty("completed") GeneratedStatusMetricStrictContract completed,
    @JsonProperty("failed") GeneratedStatusMetricStrictContract failed,
    @JsonProperty("workers") Integer workers) {

  public GeneratedJobTypeStatisticsItemStrictContract {
    Objects.requireNonNull(jobType, "No jobType provided.");
    Objects.requireNonNull(created, "No created provided.");
    Objects.requireNonNull(completed, "No completed provided.");
    Objects.requireNonNull(failed, "No failed provided.");
    Objects.requireNonNull(workers, "No workers provided.");
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

  public static JobTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements JobTypeStep, CreatedStep, CompletedStep, FailedStep, WorkersStep, OptionalStep {
    private String jobType;
    private Object created;
    private Object completed;
    private Object failed;
    private Integer workers;

    private Builder() {}

    @Override
    public CreatedStep jobType(final String jobType) {
      this.jobType = jobType;
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
    public WorkersStep failed(final Object failed) {
      this.failed = failed;
      return this;
    }

    @Override
    public OptionalStep workers(final Integer workers) {
      this.workers = workers;
      return this;
    }

    @Override
    public GeneratedJobTypeStatisticsItemStrictContract build() {
      return new GeneratedJobTypeStatisticsItemStrictContract(
          this.jobType,
          coerceCreated(this.created),
          coerceCompleted(this.completed),
          coerceFailed(this.failed),
          this.workers);
    }
  }

  public interface JobTypeStep {
    CreatedStep jobType(final String jobType);
  }

  public interface CreatedStep {
    CompletedStep created(final Object created);
  }

  public interface CompletedStep {
    FailedStep completed(final Object completed);
  }

  public interface FailedStep {
    WorkersStep failed(final Object failed);
  }

  public interface WorkersStep {
    OptionalStep workers(final Integer workers);
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
