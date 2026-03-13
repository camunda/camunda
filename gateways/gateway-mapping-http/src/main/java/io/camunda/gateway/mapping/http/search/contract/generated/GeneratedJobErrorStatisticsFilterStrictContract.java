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
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobErrorStatisticsFilterStrictContract(
    String from,
    String to,
    String jobType,
    @Nullable Object errorCode,
    @Nullable Object errorMessage) {

  public GeneratedJobErrorStatisticsFilterStrictContract {
    Objects.requireNonNull(from, "from is required and must not be null");
    Objects.requireNonNull(to, "to is required and must not be null");
    Objects.requireNonNull(jobType, "jobType is required and must not be null");
  }

  public static FromStep builder() {
    return new Builder();
  }

  public static final class Builder implements FromStep, ToStep, JobTypeStep, OptionalStep {
    private String from;
    private String to;
    private String jobType;
    private Object errorCode;
    private Object errorMessage;

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
    public OptionalStep errorCode(final @Nullable Object errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @Override
    public OptionalStep errorCode(
        final @Nullable Object errorCode, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errorCode = policy.apply(errorCode, Fields.ERROR_CODE, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(final @Nullable Object errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final @Nullable Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy) {
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
    OptionalStep errorCode(final @Nullable Object errorCode);

    OptionalStep errorCode(
        final @Nullable Object errorCode, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep errorMessage(final @Nullable Object errorMessage);

    OptionalStep errorMessage(
        final @Nullable Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy);

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
