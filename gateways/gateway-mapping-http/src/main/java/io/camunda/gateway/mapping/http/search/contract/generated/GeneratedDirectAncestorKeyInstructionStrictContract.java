/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/DirectAncestorKeyInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDirectAncestorKeyInstructionStrictContract(
    String ancestorScopeType,
    Object ancestorElementInstanceKey
) {

  public GeneratedDirectAncestorKeyInstructionStrictContract {
    Objects.requireNonNull(ancestorScopeType, "ancestorScopeType is required and must not be null");
    Objects.requireNonNull(ancestorElementInstanceKey, "ancestorElementInstanceKey is required and must not be null");
  }


  public static AncestorScopeTypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements AncestorScopeTypeStep, AncestorElementInstanceKeyStep, OptionalStep {
    private String ancestorScopeType;
    private Object ancestorElementInstanceKey;

    private Builder() {}

    @Override
    public AncestorElementInstanceKeyStep ancestorScopeType(final String ancestorScopeType) {
      this.ancestorScopeType = ancestorScopeType;
      return this;
    }

    @Override
    public OptionalStep ancestorElementInstanceKey(final Object ancestorElementInstanceKey) {
      this.ancestorElementInstanceKey = ancestorElementInstanceKey;
      return this;
    }
    @Override
    public GeneratedDirectAncestorKeyInstructionStrictContract build() {
      return new GeneratedDirectAncestorKeyInstructionStrictContract(
          this.ancestorScopeType,
          this.ancestorElementInstanceKey);
    }
  }

  public interface AncestorScopeTypeStep {
    AncestorElementInstanceKeyStep ancestorScopeType(final String ancestorScopeType);
  }

  public interface AncestorElementInstanceKeyStep {
    OptionalStep ancestorElementInstanceKey(final Object ancestorElementInstanceKey);
  }

  public interface OptionalStep {
    GeneratedDirectAncestorKeyInstructionStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef ANCESTOR_SCOPE_TYPE = ContractPolicy.field("DirectAncestorKeyInstruction", "ancestorScopeType");
    public static final ContractPolicy.FieldRef ANCESTOR_ELEMENT_INSTANCE_KEY = ContractPolicy.field("DirectAncestorKeyInstruction", "ancestorElementInstanceKey");

    private Fields() {}
  }


}
