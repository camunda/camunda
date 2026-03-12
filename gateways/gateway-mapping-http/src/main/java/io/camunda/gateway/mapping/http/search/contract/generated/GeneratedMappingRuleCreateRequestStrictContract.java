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
public record GeneratedMappingRuleCreateRequestStrictContract(String mappingRuleId) {

  public GeneratedMappingRuleCreateRequestStrictContract {
    Objects.requireNonNull(mappingRuleId, "mappingRuleId is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static MappingRuleIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements MappingRuleIdStep, OptionalStep {
    private String mappingRuleId;
    private ContractPolicy.FieldPolicy<String> mappingRuleIdPolicy;

    private Builder() {}

    @Override
    public OptionalStep mappingRuleId(
        final String mappingRuleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.mappingRuleId = mappingRuleId;
      this.mappingRuleIdPolicy = policy;
      return this;
    }

    @Override
    public GeneratedMappingRuleCreateRequestStrictContract build() {
      return new GeneratedMappingRuleCreateRequestStrictContract(
          applyRequiredPolicy(
              this.mappingRuleId, this.mappingRuleIdPolicy, Fields.MAPPING_RULE_ID));
    }
  }

  public interface MappingRuleIdStep {
    OptionalStep mappingRuleId(
        final String mappingRuleId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedMappingRuleCreateRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef MAPPING_RULE_ID =
        ContractPolicy.field("MappingRuleCreateRequest", "mappingRuleId");

    private Fields() {}
  }
}
