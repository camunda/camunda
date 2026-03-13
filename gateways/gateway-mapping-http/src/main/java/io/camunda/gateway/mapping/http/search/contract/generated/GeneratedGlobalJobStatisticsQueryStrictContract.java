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

  public static CreatedStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements CreatedStep, CompletedStep, FailedStep, IsIncompleteStep, OptionalStep {
    private Object created;
    private Object completed;
    private Object failed;
    private Boolean isIncomplete;

    private Builder() {}

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
    public IsIncompleteStep failed(final Object failed) {
      this.failed = failed;
      return this;
    }

    @Override
    public OptionalStep isIncomplete(final Boolean isIncomplete) {
      this.isIncomplete = isIncomplete;
      return this;
    }

    @Override
    public GeneratedGlobalJobStatisticsQueryStrictContract build() {
      return new GeneratedGlobalJobStatisticsQueryStrictContract(
          coerceCreated(this.created),
          coerceCompleted(this.completed),
          coerceFailed(this.failed),
          this.isIncomplete);
    }
  }

  public interface CreatedStep {
    CompletedStep created(final Object created);
  }

  public interface CompletedStep {
    FailedStep completed(final Object completed);
  }

  public interface FailedStep {
    IsIncompleteStep failed(final Object failed);
  }

  public interface IsIncompleteStep {
    OptionalStep isIncomplete(final Boolean isIncomplete);
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
