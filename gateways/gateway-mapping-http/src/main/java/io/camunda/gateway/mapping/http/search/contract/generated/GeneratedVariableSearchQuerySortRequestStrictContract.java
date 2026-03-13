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
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedVariableSearchQuerySortRequestStrictContract(
    String field, io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order) {

  public GeneratedVariableSearchQuerySortRequestStrictContract {
    Objects.requireNonNull(field, "field is required and must not be null");
  }

  public static FieldStep builder() {
    return new Builder();
  }

  public static final class Builder implements FieldStep, OptionalStep {
    private String field;
    private io.camunda.gateway.protocol.model.SortOrderEnum order;

    private Builder() {}

    @Override
    public OptionalStep field(final String field) {
      this.field = field;
      return this;
    }

    @Override
    public OptionalStep order(
        final io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order) {
      this.order = order;
      return this;
    }

    @Override
    public OptionalStep order(
        final io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.SortOrderEnum> policy) {
      this.order = policy.apply(order, Fields.ORDER, null);
      return this;
    }

    @Override
    public GeneratedVariableSearchQuerySortRequestStrictContract build() {
      return new GeneratedVariableSearchQuerySortRequestStrictContract(this.field, this.order);
    }
  }

  public interface FieldStep {
    OptionalStep field(final String field);
  }

  public interface OptionalStep {
    OptionalStep order(final io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order);

    OptionalStep order(
        final io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.SortOrderEnum> policy);

    GeneratedVariableSearchQuerySortRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FIELD =
        ContractPolicy.field("VariableSearchQuerySortRequest", "field");
    public static final ContractPolicy.FieldRef ORDER =
        ContractPolicy.field("VariableSearchQuerySortRequest", "order");

    private Fields() {}
  }
}
