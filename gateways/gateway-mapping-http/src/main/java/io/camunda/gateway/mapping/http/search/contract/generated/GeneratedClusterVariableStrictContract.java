/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
public record GeneratedClusterVariableStrictContract(String value) {

  public GeneratedClusterVariableStrictContract {
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
    public GeneratedClusterVariableStrictContract build() {
      return new GeneratedClusterVariableStrictContract(this.value);
    }
  }

  public interface ValueStep {
    OptionalStep value(final String value);
  }

  public interface OptionalStep {
    GeneratedClusterVariableStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("ClusterVariableResult", "value");

    private Fields() {}
  }
}
