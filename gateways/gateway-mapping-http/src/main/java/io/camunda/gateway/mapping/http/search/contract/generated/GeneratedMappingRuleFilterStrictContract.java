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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMappingRuleFilterStrictContract(
    @Nullable String claimName,
    @Nullable String claimValue,
    @Nullable String name,
    @Nullable String mappingRuleId) {

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String claimName;
    private String claimValue;
    private String name;
    private String mappingRuleId;

    private Builder() {}

    @Override
    public OptionalStep claimName(final String claimName) {
      this.claimName = claimName;
      return this;
    }

    @Override
    public OptionalStep claimName(
        final String claimName, final ContractPolicy.FieldPolicy<String> policy) {
      this.claimName = policy.apply(claimName, Fields.CLAIM_NAME, null);
      return this;
    }

    @Override
    public OptionalStep claimValue(final String claimValue) {
      this.claimValue = claimValue;
      return this;
    }

    @Override
    public OptionalStep claimValue(
        final String claimValue, final ContractPolicy.FieldPolicy<String> policy) {
      this.claimValue = policy.apply(claimValue, Fields.CLAIM_VALUE, null);
      return this;
    }

    @Override
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep mappingRuleId(final String mappingRuleId) {
      this.mappingRuleId = mappingRuleId;
      return this;
    }

    @Override
    public OptionalStep mappingRuleId(
        final String mappingRuleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.mappingRuleId = policy.apply(mappingRuleId, Fields.MAPPING_RULE_ID, null);
      return this;
    }

    @Override
    public GeneratedMappingRuleFilterStrictContract build() {
      return new GeneratedMappingRuleFilterStrictContract(
          this.claimName, this.claimValue, this.name, this.mappingRuleId);
    }
  }

  public interface OptionalStep {
    OptionalStep claimName(final String claimName);

    OptionalStep claimName(final String claimName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep claimValue(final String claimValue);

    OptionalStep claimValue(
        final String claimValue, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep name(final String name);

    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep mappingRuleId(final String mappingRuleId);

    OptionalStep mappingRuleId(
        final String mappingRuleId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedMappingRuleFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CLAIM_NAME =
        ContractPolicy.field("MappingRuleFilter", "claimName");
    public static final ContractPolicy.FieldRef CLAIM_VALUE =
        ContractPolicy.field("MappingRuleFilter", "claimValue");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("MappingRuleFilter", "name");
    public static final ContractPolicy.FieldRef MAPPING_RULE_ID =
        ContractPolicy.field("MappingRuleFilter", "mappingRuleId");

    private Fields() {}
  }
}
