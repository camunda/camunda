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
    private Object errorCode;
    private Object errorMessage;

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
    public OptionalStep errorCode(final Object errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @Override
    public OptionalStep errorCode(
        final Object errorCode, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errorCode = policy.apply(errorCode, Fields.ERROR_CODE, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(final Object errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

    @Override
    public GeneratedJobErrorStatisticsFilterStrictContract build() {
      return new GeneratedJobErrorStatisticsFilterStrictContract(
          applyRequiredPolicy(this.from, this.fromPolicy, Fields.FROM),
          applyRequiredPolicy(this.to, this.toPolicy, Fields.TO),
          applyRequiredPolicy(this.jobType, this.jobTypePolicy, Fields.JOB_TYPE),
          this.errorCode,
          this.errorMessage);
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
    OptionalStep errorCode(final Object errorCode);

    OptionalStep errorCode(final Object errorCode, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep errorMessage(final Object errorMessage);

    OptionalStep errorMessage(
        final Object errorMessage, final ContractPolicy.FieldPolicy<Object> policy);

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
