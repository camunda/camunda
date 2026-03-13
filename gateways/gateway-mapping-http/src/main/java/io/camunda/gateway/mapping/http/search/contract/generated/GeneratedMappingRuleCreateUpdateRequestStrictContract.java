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
import org.jspecify.annotations.NullMarked;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMappingRuleCreateUpdateRequestStrictContract(
    String claimName, String claimValue, String name) {

  public GeneratedMappingRuleCreateUpdateRequestStrictContract {
    Objects.requireNonNull(claimName, "claimName is required and must not be null");
    Objects.requireNonNull(claimValue, "claimValue is required and must not be null");
    Objects.requireNonNull(name, "name is required and must not be null");
  }

  public static ClaimNameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ClaimNameStep, ClaimValueStep, NameStep, OptionalStep {
    private String claimName;
    private String claimValue;
    private String name;

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
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public GeneratedMappingRuleCreateUpdateRequestStrictContract build() {
      return new GeneratedMappingRuleCreateUpdateRequestStrictContract(
          this.claimName, this.claimValue, this.name);
    }
  }

  public interface ClaimNameStep {
    ClaimValueStep claimName(final String claimName);
  }

  public interface ClaimValueStep {
    NameStep claimValue(final String claimValue);
  }

  public interface NameStep {
    OptionalStep name(final String name);
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
