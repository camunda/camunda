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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuthorizationSearchQuerySortRequestStrictContract(
    String field, @Nullable io.camunda.gateway.protocol.model.SortOrderEnum order) {

  public GeneratedAuthorizationSearchQuerySortRequestStrictContract {
    Objects.requireNonNull(field, "field is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static FieldStep builder() {
    return new Builder();
  }

  public static final class Builder implements FieldStep, OptionalStep {
    private String field;
    private ContractPolicy.FieldPolicy<String> fieldPolicy;
    private io.camunda.gateway.protocol.model.SortOrderEnum order;

    private Builder() {}

    @Override
    public OptionalStep field(final String field, final ContractPolicy.FieldPolicy<String> policy) {
      this.field = field;
      this.fieldPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep order(final io.camunda.gateway.protocol.model.SortOrderEnum order) {
      this.order = order;
      return this;
    }

    @Override
    public OptionalStep order(
        final io.camunda.gateway.protocol.model.SortOrderEnum order,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.SortOrderEnum> policy) {
      this.order = policy.apply(order, Fields.ORDER, null);
      return this;
    }

    @Override
    public GeneratedAuthorizationSearchQuerySortRequestStrictContract build() {
      return new GeneratedAuthorizationSearchQuerySortRequestStrictContract(
          applyRequiredPolicy(this.field, this.fieldPolicy, Fields.FIELD), this.order);
    }
  }

  public interface FieldStep {
    OptionalStep field(final String field, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep order(final io.camunda.gateway.protocol.model.SortOrderEnum order);

    OptionalStep order(
        final io.camunda.gateway.protocol.model.SortOrderEnum order,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.SortOrderEnum> policy);

    GeneratedAuthorizationSearchQuerySortRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FIELD =
        ContractPolicy.field("AuthorizationSearchQuerySortRequest", "field");
    public static final ContractPolicy.FieldRef ORDER =
        ContractPolicy.field("AuthorizationSearchQuerySortRequest", "order");

    private Fields() {}
  }
}
