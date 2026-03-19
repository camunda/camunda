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
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobTimeSeriesStatisticsFilterStrictContract(
    @JsonProperty("from") String from,
    @JsonProperty("to") String to,
    @JsonProperty("jobType") String jobType,
    @JsonProperty("resolution") @Nullable String resolution) {

  public GeneratedJobTimeSeriesStatisticsFilterStrictContract {
    Objects.requireNonNull(from, "No from provided.");
    Objects.requireNonNull(to, "No to provided.");
    Objects.requireNonNull(jobType, "No jobType provided.");
  }

  public static FromStep builder() {
    return new Builder();
  }

  public static final class Builder implements FromStep, ToStep, JobTypeStep, OptionalStep {
    private String from;
    private String to;
    private String jobType;
    private String resolution;

    private Builder() {}

    @Override
    public ToStep from(final String from) {
      this.from = from;
      return this;
    }

    @Override
    public JobTypeStep to(final String to) {
      this.to = to;
      return this;
    }

    @Override
    public OptionalStep jobType(final String jobType) {
      this.jobType = jobType;
      return this;
    }

    @Override
    public OptionalStep resolution(final @Nullable String resolution) {
      this.resolution = resolution;
      return this;
    }

    @Override
    public OptionalStep resolution(
        final @Nullable String resolution, final ContractPolicy.FieldPolicy<String> policy) {
      this.resolution = policy.apply(resolution, Fields.RESOLUTION, null);
      return this;
    }

    @Override
    public GeneratedJobTimeSeriesStatisticsFilterStrictContract build() {
      return new GeneratedJobTimeSeriesStatisticsFilterStrictContract(
          this.from, this.to, this.jobType, this.resolution);
    }
  }

  public interface FromStep {
    ToStep from(final String from);
  }

  public interface ToStep {
    JobTypeStep to(final String to);
  }

  public interface JobTypeStep {
    OptionalStep jobType(final String jobType);
  }

  public interface OptionalStep {
    OptionalStep resolution(final @Nullable String resolution);

    OptionalStep resolution(
        final @Nullable String resolution, final ContractPolicy.FieldPolicy<String> policy);

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
