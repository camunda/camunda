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
public record GeneratedMappingRuleCreateRequestStrictContract(String mappingRuleId) {

  public GeneratedMappingRuleCreateRequestStrictContract {
    Objects.requireNonNull(mappingRuleId, "mappingRuleId is required and must not be null");
  }

  public static MappingRuleIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements MappingRuleIdStep, OptionalStep {
    private String mappingRuleId;

    private Builder() {}

    @Override
    public OptionalStep mappingRuleId(final String mappingRuleId) {
      this.mappingRuleId = mappingRuleId;
      return this;
    }

    @Override
    public GeneratedMappingRuleCreateRequestStrictContract build() {
      return new GeneratedMappingRuleCreateRequestStrictContract(this.mappingRuleId);
    }
  }

  public interface MappingRuleIdStep {
    OptionalStep mappingRuleId(final String mappingRuleId);
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
