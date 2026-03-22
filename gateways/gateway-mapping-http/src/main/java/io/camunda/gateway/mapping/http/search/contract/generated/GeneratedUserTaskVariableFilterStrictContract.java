/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskVariableFilterStrictContract(
    @JsonProperty("name") @Nullable GeneratedStringFilterPropertyStrictContract name) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedStringFilterPropertyStrictContract name;

    private Builder() {}

    @Override
    public OptionalStep name(final @Nullable GeneratedStringFilterPropertyStrictContract name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(
        final @Nullable GeneratedStringFilterPropertyStrictContract name,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public GeneratedUserTaskVariableFilterStrictContract build() {
      return new GeneratedUserTaskVariableFilterStrictContract(this.name);
    }
  }

  public interface OptionalStep {
    OptionalStep name(final @Nullable GeneratedStringFilterPropertyStrictContract name);

    OptionalStep name(
        final @Nullable GeneratedStringFilterPropertyStrictContract name,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    GeneratedUserTaskVariableFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("UserTaskVariableFilter", "name");

    private Fields() {}
  }
}
