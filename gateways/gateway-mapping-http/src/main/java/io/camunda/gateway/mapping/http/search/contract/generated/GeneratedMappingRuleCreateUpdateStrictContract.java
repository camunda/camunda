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
public record GeneratedMappingRuleCreateUpdateStrictContract(
    String claimName, String claimValue, String name, String mappingRuleId) {

  public GeneratedMappingRuleCreateUpdateStrictContract {
    Objects.requireNonNull(claimName, "claimName is required and must not be null");
    Objects.requireNonNull(claimValue, "claimValue is required and must not be null");
    Objects.requireNonNull(name, "name is required and must not be null");
    Objects.requireNonNull(mappingRuleId, "mappingRuleId is required and must not be null");
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
      implements ClaimNameStep, ClaimValueStep, NameStep, MappingRuleIdStep, OptionalStep {
    private String claimName;
    private ContractPolicy.FieldPolicy<String> claimNamePolicy;
    private String claimValue;
    private ContractPolicy.FieldPolicy<String> claimValuePolicy;
    private String name;
    private ContractPolicy.FieldPolicy<String> namePolicy;
    private String mappingRuleId;
    private ContractPolicy.FieldPolicy<String> mappingRuleIdPolicy;

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
    public MappingRuleIdStep name(
        final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep mappingRuleId(
        final String mappingRuleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.mappingRuleId = mappingRuleId;
      this.mappingRuleIdPolicy = policy;
      return this;
    }

    @Override
    public GeneratedMappingRuleCreateUpdateStrictContract build() {
      return new GeneratedMappingRuleCreateUpdateStrictContract(
          applyRequiredPolicy(this.claimName, this.claimNamePolicy, Fields.CLAIM_NAME),
          applyRequiredPolicy(this.claimValue, this.claimValuePolicy, Fields.CLAIM_VALUE),
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME),
          applyRequiredPolicy(
              this.mappingRuleId, this.mappingRuleIdPolicy, Fields.MAPPING_RULE_ID));
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
    MappingRuleIdStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MappingRuleIdStep {
    OptionalStep mappingRuleId(
        final String mappingRuleId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedMappingRuleCreateUpdateStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CLAIM_NAME =
        ContractPolicy.field("MappingRuleCreateUpdateResult", "claimName");
    public static final ContractPolicy.FieldRef CLAIM_VALUE =
        ContractPolicy.field("MappingRuleCreateUpdateResult", "claimValue");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("MappingRuleCreateUpdateResult", "name");
    public static final ContractPolicy.FieldRef MAPPING_RULE_ID =
        ContractPolicy.field("MappingRuleCreateUpdateResult", "mappingRuleId");

    private Fields() {}
  }
}
