/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/UseSourceParentKeyInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUseSourceParentKeyInstructionStrictContract(
    @JsonProperty("ancestorScopeType") String ancestorScopeType)
    implements GeneratedAncestorScopeInstructionStrictContract {

  public GeneratedUseSourceParentKeyInstructionStrictContract {
    Objects.requireNonNull(ancestorScopeType, "No ancestorScopeType provided.");
  }

  public static AncestorScopeTypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements AncestorScopeTypeStep, OptionalStep {
    private String ancestorScopeType;

    private Builder() {}

    @Override
    public OptionalStep ancestorScopeType(final String ancestorScopeType) {
      this.ancestorScopeType = ancestorScopeType;
      return this;
    }

    @Override
    public GeneratedUseSourceParentKeyInstructionStrictContract build() {
      return new GeneratedUseSourceParentKeyInstructionStrictContract(this.ancestorScopeType);
    }
  }

  public interface AncestorScopeTypeStep {
    OptionalStep ancestorScopeType(final String ancestorScopeType);
  }

  public interface OptionalStep {
    GeneratedUseSourceParentKeyInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ANCESTOR_SCOPE_TYPE =
        ContractPolicy.field("UseSourceParentKeyInstruction", "ancestorScopeType");

    private Fields() {}
  }
}
