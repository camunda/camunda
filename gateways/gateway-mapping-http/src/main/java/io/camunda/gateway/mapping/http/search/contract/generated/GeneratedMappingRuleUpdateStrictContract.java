/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/mapping-rules.yaml#/components/schemas/MappingRuleUpdateResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMappingRuleUpdateStrictContract(
    @JsonProperty("claimName") String claimName,
    @JsonProperty("claimValue") String claimValue,
    @JsonProperty("name") String name,
    @JsonProperty("mappingRuleId") String mappingRuleId) {

  public GeneratedMappingRuleUpdateStrictContract {
    Objects.requireNonNull(claimName, "No claimName provided.");
    Objects.requireNonNull(claimValue, "No claimValue provided.");
    Objects.requireNonNull(name, "No name provided.");
    Objects.requireNonNull(mappingRuleId, "No mappingRuleId provided.");
  }

  public static ClaimNameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ClaimNameStep, ClaimValueStep, NameStep, MappingRuleIdStep, OptionalStep {
    private String claimName;
    private String claimValue;
    private String name;
    private String mappingRuleId;

    private Builder() {}

    @Override
    public ClaimValueStep claimName(final String claimName) {
      this.claimName = claimName;
      return this;
    }

    @Override
    public NameStep claimValue(final String claimValue) {
      this.claimValue = claimValue;
      return this;
    }

    @Override
    public MappingRuleIdStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep mappingRuleId(final String mappingRuleId) {
      this.mappingRuleId = mappingRuleId;
      return this;
    }

    @Override
    public GeneratedMappingRuleUpdateStrictContract build() {
      return new GeneratedMappingRuleUpdateStrictContract(
          this.claimName, this.claimValue, this.name, this.mappingRuleId);
    }
  }

  public interface ClaimNameStep {
    ClaimValueStep claimName(final String claimName);
  }

  public interface ClaimValueStep {
    NameStep claimValue(final String claimValue);
  }

  public interface NameStep {
    MappingRuleIdStep name(final String name);
  }

  public interface MappingRuleIdStep {
    OptionalStep mappingRuleId(final String mappingRuleId);
  }

  public interface OptionalStep {
    GeneratedMappingRuleUpdateStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CLAIM_NAME =
        ContractPolicy.field("MappingRuleUpdateResult", "claimName");
    public static final ContractPolicy.FieldRef CLAIM_VALUE =
        ContractPolicy.field("MappingRuleUpdateResult", "claimValue");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("MappingRuleUpdateResult", "name");
    public static final ContractPolicy.FieldRef MAPPING_RULE_ID =
        ContractPolicy.field("MappingRuleUpdateResult", "mappingRuleId");

    private Fields() {}
  }
}
