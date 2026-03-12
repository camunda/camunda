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
public record GeneratedClusterVariableSearchStrictContract(String value, Boolean isTruncated) {

  public GeneratedClusterVariableSearchStrictContract {
    Objects.requireNonNull(value, "value is required and must not be null");
    Objects.requireNonNull(isTruncated, "isTruncated is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ValueStep builder() {
    return new Builder();
  }

  public static final class Builder implements ValueStep, IsTruncatedStep, OptionalStep {
    private String value;
    private ContractPolicy.FieldPolicy<String> valuePolicy;
    private Boolean isTruncated;
    private ContractPolicy.FieldPolicy<Boolean> isTruncatedPolicy;

    private Builder() {}

    @Override
    public IsTruncatedStep value(
        final String value, final ContractPolicy.FieldPolicy<String> policy) {
      this.value = value;
      this.valuePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep isTruncated(
        final Boolean isTruncated, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isTruncated = isTruncated;
      this.isTruncatedPolicy = policy;
      return this;
    }

    @Override
    public GeneratedClusterVariableSearchStrictContract build() {
      return new GeneratedClusterVariableSearchStrictContract(
          applyRequiredPolicy(this.value, this.valuePolicy, Fields.VALUE),
          applyRequiredPolicy(this.isTruncated, this.isTruncatedPolicy, Fields.IS_TRUNCATED));
    }
  }

  public interface ValueStep {
    IsTruncatedStep value(final String value, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface IsTruncatedStep {
    OptionalStep isTruncated(
        final Boolean isTruncated, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface OptionalStep {
    GeneratedClusterVariableSearchStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("ClusterVariableSearchResult", "value");
    public static final ContractPolicy.FieldRef IS_TRUNCATED =
        ContractPolicy.field("ClusterVariableSearchResult", "isTruncated");

    private Fields() {}
  }
}
