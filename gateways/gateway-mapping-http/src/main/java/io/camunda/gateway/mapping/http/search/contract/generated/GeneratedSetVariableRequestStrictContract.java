/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/variables.yaml#/components/schemas/SetVariableRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSetVariableRequestStrictContract(
    java.util.Map<String, Object> variables,
    @Nullable Boolean local,
    @Nullable Long operationReference
) {

  public GeneratedSetVariableRequestStrictContract {
    Objects.requireNonNull(variables, "variables is required and must not be null");
  }


  public static VariablesStep builder() {
    return new Builder();
  }

  public static final class Builder implements VariablesStep, OptionalStep {
    private java.util.Map<String, Object> variables;
    private Boolean local;
    private Long operationReference;

    private Builder() {}

    @Override
    public OptionalStep variables(final java.util.Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep local(final @Nullable Boolean local) {
      this.local = local;
      return this;
    }

    @Override
    public OptionalStep local(final @Nullable Boolean local, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.local = policy.apply(local, Fields.LOCAL, null);
      return this;
    }


    @Override
    public OptionalStep operationReference(final @Nullable Long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    @Override
    public OptionalStep operationReference(final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy) {
      this.operationReference = policy.apply(operationReference, Fields.OPERATION_REFERENCE, null);
      return this;
    }

    @Override
    public GeneratedSetVariableRequestStrictContract build() {
      return new GeneratedSetVariableRequestStrictContract(
          this.variables,
          this.local,
          this.operationReference);
    }
  }

  public interface VariablesStep {
    OptionalStep variables(final java.util.Map<String, Object> variables);
  }

  public interface OptionalStep {
  OptionalStep local(final @Nullable Boolean local);

  OptionalStep local(final @Nullable Boolean local, final ContractPolicy.FieldPolicy<Boolean> policy);


  OptionalStep operationReference(final @Nullable Long operationReference);

  OptionalStep operationReference(final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);


    GeneratedSetVariableRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef VARIABLES = ContractPolicy.field("SetVariableRequest", "variables");
    public static final ContractPolicy.FieldRef LOCAL = ContractPolicy.field("SetVariableRequest", "local");
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE = ContractPolicy.field("SetVariableRequest", "operationReference");

    private Fields() {}
  }


}
