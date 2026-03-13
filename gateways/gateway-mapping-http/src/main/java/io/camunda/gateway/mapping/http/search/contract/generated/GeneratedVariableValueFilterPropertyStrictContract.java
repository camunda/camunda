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
public record GeneratedVariableValueFilterPropertyStrictContract(String name, Object value) {

  public GeneratedVariableValueFilterPropertyStrictContract {
    Objects.requireNonNull(name, "name is required and must not be null");
    Objects.requireNonNull(value, "value is required and must not be null");
  }

  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder implements NameStep, ValueStep, OptionalStep {
    private String name;
    private Object value;

    private Builder() {}

    @Override
    public ValueStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep value(final Object value) {
      this.value = value;
      return this;
    }

    @Override
    public GeneratedVariableValueFilterPropertyStrictContract build() {
      return new GeneratedVariableValueFilterPropertyStrictContract(this.name, this.value);
    }
  }

  public interface NameStep {
    ValueStep name(final String name);
  }

  public interface ValueStep {
    OptionalStep value(final Object value);
  }

  public interface OptionalStep {
    GeneratedVariableValueFilterPropertyStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("VariableValueFilterProperty", "name");
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("VariableValueFilterProperty", "value");

    private Fields() {}
  }
}
