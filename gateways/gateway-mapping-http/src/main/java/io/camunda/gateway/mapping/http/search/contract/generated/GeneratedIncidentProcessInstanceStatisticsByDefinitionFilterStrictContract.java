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
public record GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract(
    Integer errorHashCode) {

  public GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract {
    Objects.requireNonNull(errorHashCode, "errorHashCode is required and must not be null");
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

  public static final class Builder implements ErrorHashCodeStep, OptionalStep {
    private Integer errorHashCode;
    private ContractPolicy.FieldPolicy<Integer> errorHashCodePolicy;

    private Builder() {}

    @Override
    public OptionalStep errorHashCode(
        final Integer errorHashCode, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.errorHashCode = errorHashCode;
      this.errorHashCodePolicy = policy;
      return this;
    }

    @Override
    public GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract build() {
      return new GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract(
          applyRequiredPolicy(
              this.errorHashCode, this.errorHashCodePolicy, Fields.ERROR_HASH_CODE));
    }
  }

  public interface ErrorHashCodeStep {
    OptionalStep errorHashCode(
        final Integer errorHashCode, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OptionalStep {
    GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ERROR_HASH_CODE =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByDefinitionFilter", "errorHashCode");

    private Fields() {}
  }
}
