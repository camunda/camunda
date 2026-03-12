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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobErrorStatisticsItemStrictContract(
    String errorCode, String errorMessage, Integer workers) {

  public GeneratedJobErrorStatisticsItemStrictContract {
    Objects.requireNonNull(errorCode, "errorCode is required and must not be null");
    Objects.requireNonNull(errorMessage, "errorMessage is required and must not be null");
    Objects.requireNonNull(workers, "workers is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ErrorCodeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ErrorCodeStep, ErrorMessageStep, WorkersStep, OptionalStep {
    private String errorCode;
    private ContractPolicy.FieldPolicy<String> errorCodePolicy;
    private String errorMessage;
    private ContractPolicy.FieldPolicy<String> errorMessagePolicy;
    private Integer workers;
    private ContractPolicy.FieldPolicy<Integer> workersPolicy;

    private Builder() {}

    @Override
    public ErrorMessageStep errorCode(
        final String errorCode, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorCode = errorCode;
      this.errorCodePolicy = policy;
      return this;
    }

    @Override
    public WorkersStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = errorMessage;
      this.errorMessagePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep workers(
        final Integer workers, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.workers = workers;
      this.workersPolicy = policy;
      return this;
    }

    @Override
    public GeneratedJobErrorStatisticsItemStrictContract build() {
      return new GeneratedJobErrorStatisticsItemStrictContract(
          applyRequiredPolicy(this.errorCode, this.errorCodePolicy, Fields.ERROR_CODE),
          applyRequiredPolicy(this.errorMessage, this.errorMessagePolicy, Fields.ERROR_MESSAGE),
          applyRequiredPolicy(this.workers, this.workersPolicy, Fields.WORKERS));
    }
  }

  public interface ErrorCodeStep {
    ErrorMessageStep errorCode(
        final String errorCode, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ErrorMessageStep {
    WorkersStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface WorkersStep {
    OptionalStep workers(final Integer workers, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OptionalStep {
    GeneratedJobErrorStatisticsItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ERROR_CODE =
        ContractPolicy.field("JobErrorStatisticsItem", "errorCode");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("JobErrorStatisticsItem", "errorMessage");
    public static final ContractPolicy.FieldRef WORKERS =
        ContractPolicy.field("JobErrorStatisticsItem", "workers");

    private Fields() {}
  }
}
