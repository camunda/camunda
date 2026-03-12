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
public record GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract(
    Integer errorHashCode, String errorMessage, Long activeInstancesWithErrorCount) {

  public GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract {
    Objects.requireNonNull(errorHashCode, "errorHashCode is required and must not be null");
    Objects.requireNonNull(errorMessage, "errorMessage is required and must not be null");
    Objects.requireNonNull(
        activeInstancesWithErrorCount,
        "activeInstancesWithErrorCount is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ErrorHashCodeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ErrorHashCodeStep,
          ErrorMessageStep,
          ActiveInstancesWithErrorCountStep,
          OptionalStep {
    private Integer errorHashCode;
    private ContractPolicy.FieldPolicy<Integer> errorHashCodePolicy;
    private String errorMessage;
    private ContractPolicy.FieldPolicy<String> errorMessagePolicy;
    private Long activeInstancesWithErrorCount;
    private ContractPolicy.FieldPolicy<Long> activeInstancesWithErrorCountPolicy;

    private Builder() {}

    @Override
    public ErrorMessageStep errorHashCode(
        final Integer errorHashCode, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.errorHashCode = errorHashCode;
      this.errorHashCodePolicy = policy;
      return this;
    }

    @Override
    public ActiveInstancesWithErrorCountStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = errorMessage;
      this.errorMessagePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep activeInstancesWithErrorCount(
        final Long activeInstancesWithErrorCount, final ContractPolicy.FieldPolicy<Long> policy) {
      this.activeInstancesWithErrorCount = activeInstancesWithErrorCount;
      this.activeInstancesWithErrorCountPolicy = policy;
      return this;
    }

    @Override
    public GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract build() {
      return new GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract(
          applyRequiredPolicy(this.errorHashCode, this.errorHashCodePolicy, Fields.ERROR_HASH_CODE),
          applyRequiredPolicy(this.errorMessage, this.errorMessagePolicy, Fields.ERROR_MESSAGE),
          applyRequiredPolicy(
              this.activeInstancesWithErrorCount,
              this.activeInstancesWithErrorCountPolicy,
              Fields.ACTIVE_INSTANCES_WITH_ERROR_COUNT));
    }
  }

  public interface ErrorHashCodeStep {
    ErrorMessageStep errorHashCode(
        final Integer errorHashCode, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ErrorMessageStep {
    ActiveInstancesWithErrorCountStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ActiveInstancesWithErrorCountStep {
    OptionalStep activeInstancesWithErrorCount(
        final Long activeInstancesWithErrorCount, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface OptionalStep {
    GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ERROR_HASH_CODE =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByErrorResult", "errorHashCode");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByErrorResult", "errorMessage");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITH_ERROR_COUNT =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByErrorResult", "activeInstancesWithErrorCount");

    private Fields() {}
  }
}
