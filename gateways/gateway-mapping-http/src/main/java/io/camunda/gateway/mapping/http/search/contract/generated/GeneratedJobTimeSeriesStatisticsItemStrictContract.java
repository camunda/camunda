/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobTimeSeriesStatisticsItemStrictContract(
    String time,
    GeneratedStatusMetricStrictContract created,
    GeneratedStatusMetricStrictContract completed,
    GeneratedStatusMetricStrictContract failed) {

  public GeneratedJobTimeSeriesStatisticsItemStrictContract {
    Objects.requireNonNull(time, "time is required and must not be null");
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

  public static TimeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TimeStep, CreatedStep, CompletedStep, FailedStep, OptionalStep {
    private String time;
    private Object created;
    private Object completed;
    private Object failed;

    private Builder() {}

    @Override
    public CreatedStep time(final String time) {
      this.time = time;
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
    public GeneratedJobTimeSeriesStatisticsItemStrictContract build() {
      return new GeneratedJobTimeSeriesStatisticsItemStrictContract(
          this.time,
          coerceCreated(this.created),
          coerceCompleted(this.completed),
          coerceFailed(this.failed));
    }
  }

  public interface TimeStep {
    CreatedStep time(final String time);
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
    GeneratedJobTimeSeriesStatisticsItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TIME =
        ContractPolicy.field("JobTimeSeriesStatisticsItem", "time");
    public static final ContractPolicy.FieldRef CREATED =
        ContractPolicy.field("JobTimeSeriesStatisticsItem", "created");
    public static final ContractPolicy.FieldRef COMPLETED =
        ContractPolicy.field("JobTimeSeriesStatisticsItem", "completed");
    public static final ContractPolicy.FieldRef FAILED =
        ContractPolicy.field("JobTimeSeriesStatisticsItem", "failed");

    private Fields() {}
  }
}
