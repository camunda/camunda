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
public record GeneratedMappingRuleCreateUpdateRequestStrictContract(
    String claimName, String claimValue, String name) {

  public GeneratedMappingRuleCreateUpdateRequestStrictContract {
    Objects.requireNonNull(claimName, "claimName is required and must not be null");
    Objects.requireNonNull(claimValue, "claimValue is required and must not be null");
    Objects.requireNonNull(name, "name is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ClaimNameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ClaimNameStep, ClaimValueStep, NameStep, OptionalStep {
    private String claimName;
    private ContractPolicy.FieldPolicy<String> claimNamePolicy;
    private String claimValue;
    private ContractPolicy.FieldPolicy<String> claimValuePolicy;
    private String name;
    private ContractPolicy.FieldPolicy<String> namePolicy;

    private Builder() {}

    @Override
    public ClaimValueStep claimName(
        final String claimName, final ContractPolicy.FieldPolicy<String> policy) {
      this.claimName = claimName;
      this.claimNamePolicy = policy;
      return this;
    }

    @Override
    public NameStep claimValue(
        final String claimValue, final ContractPolicy.FieldPolicy<String> policy) {
      this.claimValue = claimValue;
      this.claimValuePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public GeneratedMappingRuleCreateUpdateRequestStrictContract build() {
      return new GeneratedMappingRuleCreateUpdateRequestStrictContract(
          applyRequiredPolicy(this.claimName, this.claimNamePolicy, Fields.CLAIM_NAME),
          applyRequiredPolicy(this.claimValue, this.claimValuePolicy, Fields.CLAIM_VALUE),
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME));
    }
  }

  public interface ClaimNameStep {
    ClaimValueStep claimName(
        final String claimName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ClaimValueStep {
    NameStep claimValue(final String claimValue, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface NameStep {
    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedMappingRuleCreateUpdateRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CLAIM_NAME =
        ContractPolicy.field("MappingRuleCreateUpdateRequest", "claimName");
    public static final ContractPolicy.FieldRef CLAIM_VALUE =
        ContractPolicy.field("MappingRuleCreateUpdateRequest", "claimValue");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("MappingRuleCreateUpdateRequest", "name");

    private Fields() {}
  }
}
