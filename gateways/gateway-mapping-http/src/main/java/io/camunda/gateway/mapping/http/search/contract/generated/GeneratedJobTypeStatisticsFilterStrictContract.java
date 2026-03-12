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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobTypeStatisticsFilterStrictContract(
    String from, String to, @Nullable Object jobType) {

  public GeneratedJobTypeStatisticsFilterStrictContract {
    Objects.requireNonNull(from, "from is required and must not be null");
    Objects.requireNonNull(to, "to is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static FromStep builder() {
    return new Builder();
  }

  public static final class Builder implements FromStep, ToStep, OptionalStep {
    private String from;
    private ContractPolicy.FieldPolicy<String> fromPolicy;
    private String to;
    private ContractPolicy.FieldPolicy<String> toPolicy;
    private Object jobType;

    private Builder() {}

    @Override
    public ToStep from(final String from, final ContractPolicy.FieldPolicy<String> policy) {
      this.from = from;
      this.fromPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep to(final String to, final ContractPolicy.FieldPolicy<String> policy) {
      this.to = to;
      this.toPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep jobType(final Object jobType) {
      this.jobType = jobType;
      return this;
    }

    @Override
    public OptionalStep jobType(
        final Object jobType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobType = policy.apply(jobType, Fields.JOB_TYPE, null);
      return this;
    }

    @Override
    public GeneratedJobTypeStatisticsFilterStrictContract build() {
      return new GeneratedJobTypeStatisticsFilterStrictContract(
          applyRequiredPolicy(this.from, this.fromPolicy, Fields.FROM),
          applyRequiredPolicy(this.to, this.toPolicy, Fields.TO),
          this.jobType);
    }
  }

  public interface FromStep {
    ToStep from(final String from, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ToStep {
    OptionalStep to(final String to, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep jobType(final Object jobType);

    OptionalStep jobType(final Object jobType, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedJobTypeStatisticsFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FROM =
        ContractPolicy.field("JobTypeStatisticsFilter", "from");
    public static final ContractPolicy.FieldRef TO =
        ContractPolicy.field("JobTypeStatisticsFilter", "to");
    public static final ContractPolicy.FieldRef JOB_TYPE =
        ContractPolicy.field("JobTypeStatisticsFilter", "jobType");

    private Fields() {}
  }
}
