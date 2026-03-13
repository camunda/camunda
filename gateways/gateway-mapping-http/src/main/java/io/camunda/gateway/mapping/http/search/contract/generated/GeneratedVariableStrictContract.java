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
public record GeneratedVariableStrictContract(String value) {

  public GeneratedVariableStrictContract {
    Objects.requireNonNull(value, "value is required and must not be null");
  }

  public static ValueStep builder() {
    return new Builder();
  }

  public static final class Builder implements ValueStep, OptionalStep {
    private String value;

    private Builder() {}

    @Override
    public OptionalStep value(final String value) {
      this.value = value;
      return this;
    }

    @Override
    public GeneratedVariableStrictContract build() {
      return new GeneratedVariableStrictContract(this.value);
    }
  }

  public interface ValueStep {
    OptionalStep value(final String value);
  }

  public interface OptionalStep {
    GeneratedVariableStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("VariableResult", "value");

    private Fields() {}
  }
}
