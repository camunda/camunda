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
public record GeneratedJobTimeSeriesStatisticsFilterStrictContract(
    String from, String to, String jobType, @Nullable String resolution) {

  public GeneratedJobTimeSeriesStatisticsFilterStrictContract {
    Objects.requireNonNull(from, "from is required and must not be null");
    Objects.requireNonNull(to, "to is required and must not be null");
    Objects.requireNonNull(jobType, "jobType is required and must not be null");
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

  public static final class Builder implements FromStep, ToStep, JobTypeStep, OptionalStep {
    private String from;
    private ContractPolicy.FieldPolicy<String> fromPolicy;
    private String to;
    private ContractPolicy.FieldPolicy<String> toPolicy;
    private String jobType;
    private ContractPolicy.FieldPolicy<String> jobTypePolicy;
    private String resolution;

    private Builder() {}

    @Override
    public ToStep from(final String from, final ContractPolicy.FieldPolicy<String> policy) {
      this.from = from;
      this.fromPolicy = policy;
      return this;
    }

    @Override
    public JobTypeStep to(final String to, final ContractPolicy.FieldPolicy<String> policy) {
      this.to = to;
      this.toPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep jobType(
        final String jobType, final ContractPolicy.FieldPolicy<String> policy) {
      this.jobType = jobType;
      this.jobTypePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep resolution(final String resolution) {
      this.resolution = resolution;
      return this;
    }

    @Override
    public OptionalStep resolution(
        final String resolution, final ContractPolicy.FieldPolicy<String> policy) {
      this.resolution = policy.apply(resolution, Fields.RESOLUTION, null);
      return this;
    }

    @Override
    public GeneratedJobTimeSeriesStatisticsFilterStrictContract build() {
      return new GeneratedJobTimeSeriesStatisticsFilterStrictContract(
          applyRequiredPolicy(this.from, this.fromPolicy, Fields.FROM),
          applyRequiredPolicy(this.to, this.toPolicy, Fields.TO),
          applyRequiredPolicy(this.jobType, this.jobTypePolicy, Fields.JOB_TYPE),
          this.resolution);
    }
  }

  public interface FromStep {
    ToStep from(final String from, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ToStep {
    JobTypeStep to(final String to, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface JobTypeStep {
    OptionalStep jobType(final String jobType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep resolution(final String resolution);

    OptionalStep resolution(
        final String resolution, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedJobTimeSeriesStatisticsFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FROM =
        ContractPolicy.field("JobTimeSeriesStatisticsFilter", "from");
    public static final ContractPolicy.FieldRef TO =
        ContractPolicy.field("JobTimeSeriesStatisticsFilter", "to");
    public static final ContractPolicy.FieldRef JOB_TYPE =
        ContractPolicy.field("JobTimeSeriesStatisticsFilter", "jobType");
    public static final ContractPolicy.FieldRef RESOLUTION =
        ContractPolicy.field("JobTimeSeriesStatisticsFilter", "resolution");

    private Fields() {}
  }
}
