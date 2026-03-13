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
public record GeneratedUpdateClusterVariableRequestStrictContract(
    java.util.Map<String, Object> value) {

  public GeneratedUpdateClusterVariableRequestStrictContract {
    Objects.requireNonNull(value, "value is required and must not be null");
  }

  public static ValueStep builder() {
    return new Builder();
  }

  public static final class Builder implements ValueStep, OptionalStep {
    private java.util.Map<String, Object> value;

    private Builder() {}

    @Override
    public OptionalStep value(final java.util.Map<String, Object> value) {
      this.value = value;
      return this;
    }

    @Override
    public GeneratedUpdateClusterVariableRequestStrictContract build() {
      return new GeneratedUpdateClusterVariableRequestStrictContract(this.value);
    }
  }

  public interface ValueStep {
    OptionalStep value(final java.util.Map<String, Object> value);
  }

  public interface OptionalStep {
    GeneratedUpdateClusterVariableRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("UpdateClusterVariableRequest", "value");

    private Fields() {}
  }
}
