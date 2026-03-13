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
public record GeneratedVariableSearchStrictContract(String value, Boolean isTruncated) {

  public GeneratedVariableSearchStrictContract {
    Objects.requireNonNull(value, "value is required and must not be null");
    Objects.requireNonNull(isTruncated, "isTruncated is required and must not be null");
  }

  public static ValueStep builder() {
    return new Builder();
  }

  public static final class Builder implements ValueStep, IsTruncatedStep, OptionalStep {
    private String value;
    private Boolean isTruncated;

    private Builder() {}

    @Override
    public IsTruncatedStep value(final String value) {
      this.value = value;
      return this;
    }

    @Override
    public OptionalStep isTruncated(final Boolean isTruncated) {
      this.isTruncated = isTruncated;
      return this;
    }

    @Override
    public GeneratedVariableSearchStrictContract build() {
      return new GeneratedVariableSearchStrictContract(this.value, this.isTruncated);
    }
  }

  public interface ValueStep {
    IsTruncatedStep value(final String value);
  }

  public interface IsTruncatedStep {
    OptionalStep isTruncated(final Boolean isTruncated);
  }

  public interface OptionalStep {
    GeneratedVariableSearchStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("VariableSearchResult", "value");
    public static final ContractPolicy.FieldRef IS_TRUNCATED =
        ContractPolicy.field("VariableSearchResult", "isTruncated");

    private Fields() {}
  }
}
