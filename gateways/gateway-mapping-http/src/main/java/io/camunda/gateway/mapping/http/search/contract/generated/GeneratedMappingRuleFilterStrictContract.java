/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/mapping-rules.yaml#/components/schemas/MappingRuleFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMappingRuleFilterStrictContract(
    @Nullable String claimName,
    @Nullable String claimValue,
    @Nullable String name,
    @Nullable String mappingRuleId
) {


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
    public OptionalStep claimName(final @Nullable String claimName) {
      this.claimName = claimName;
      return this;
    }

    @Override
    public OptionalStep claimName(final @Nullable String claimName, final ContractPolicy.FieldPolicy<String> policy) {
      this.claimName = policy.apply(claimName, Fields.CLAIM_NAME, null);
      return this;
    }


    @Override
    public OptionalStep claimValue(final @Nullable String claimValue) {
      this.claimValue = claimValue;
      return this;
    }

    @Override
    public OptionalStep claimValue(final @Nullable String claimValue, final ContractPolicy.FieldPolicy<String> policy) {
      this.claimValue = policy.apply(claimValue, Fields.CLAIM_VALUE, null);
      return this;
    }


    @Override
    public OptionalStep name(final @Nullable String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }


    @Override
    public OptionalStep mappingRuleId(final @Nullable String mappingRuleId) {
      this.mappingRuleId = mappingRuleId;
      return this;
    }

    @Override
    public OptionalStep mappingRuleId(final @Nullable String mappingRuleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.mappingRuleId = policy.apply(mappingRuleId, Fields.MAPPING_RULE_ID, null);
      return this;
    }

    @Override
    public GeneratedMappingRuleFilterStrictContract build() {
      return new GeneratedMappingRuleFilterStrictContract(
          this.claimName,
          this.claimValue,
          this.name,
          this.mappingRuleId);
    }
  }

  public interface OptionalStep {
  OptionalStep claimName(final @Nullable String claimName);

  OptionalStep claimName(final @Nullable String claimName, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep claimValue(final @Nullable String claimValue);

  OptionalStep claimValue(final @Nullable String claimValue, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep name(final @Nullable String name);

  OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep mappingRuleId(final @Nullable String mappingRuleId);

  OptionalStep mappingRuleId(final @Nullable String mappingRuleId, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedMappingRuleFilterStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef CLAIM_NAME = ContractPolicy.field("MappingRuleFilter", "claimName");
    public static final ContractPolicy.FieldRef CLAIM_VALUE = ContractPolicy.field("MappingRuleFilter", "claimValue");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("MappingRuleFilter", "name");
    public static final ContractPolicy.FieldRef MAPPING_RULE_ID = ContractPolicy.field("MappingRuleFilter", "mappingRuleId");

    private Fields() {}
  }


}
