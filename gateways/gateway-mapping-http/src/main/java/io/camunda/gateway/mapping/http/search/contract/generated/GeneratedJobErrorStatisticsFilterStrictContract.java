/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/job-metrics.yaml#/components/schemas/JobErrorStatisticsFilter
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
public record GeneratedJobErrorStatisticsFilterStrictContract(
    @JsonProperty("from") String from,
    @JsonProperty("to") String to,
    @JsonProperty("jobType") String jobType,
    @JsonProperty("errorCode") @Nullable GeneratedStringFilterPropertyStrictContract errorCode,
    @JsonProperty("errorMessage")
        @Nullable GeneratedStringFilterPropertyStrictContract errorMessage) {

  public GeneratedJobErrorStatisticsFilterStrictContract {
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
    private GeneratedStringFilterPropertyStrictContract errorCode;
    private GeneratedStringFilterPropertyStrictContract errorMessage;

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
    public OptionalStep errorCode(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @Override
    public OptionalStep errorCode(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorCode,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.errorCode = policy.apply(errorCode, Fields.ERROR_CODE, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

    @Override
    public GeneratedJobErrorStatisticsFilterStrictContract build() {
      return new GeneratedJobErrorStatisticsFilterStrictContract(
          this.from, this.to, this.jobType, this.errorCode, this.errorMessage);
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
    OptionalStep errorCode(final @Nullable GeneratedStringFilterPropertyStrictContract errorCode);

    OptionalStep errorCode(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorCode,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage);

    OptionalStep errorMessage(
        final @Nullable GeneratedStringFilterPropertyStrictContract errorMessage,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    GeneratedJobErrorStatisticsFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FROM =
        ContractPolicy.field("JobErrorStatisticsFilter", "from");
    public static final ContractPolicy.FieldRef TO =
        ContractPolicy.field("JobErrorStatisticsFilter", "to");
    public static final ContractPolicy.FieldRef JOB_TYPE =
        ContractPolicy.field("JobErrorStatisticsFilter", "jobType");
    public static final ContractPolicy.FieldRef ERROR_CODE =
        ContractPolicy.field("JobErrorStatisticsFilter", "errorCode");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("JobErrorStatisticsFilter", "errorMessage");

    private Fields() {}
  }
}
