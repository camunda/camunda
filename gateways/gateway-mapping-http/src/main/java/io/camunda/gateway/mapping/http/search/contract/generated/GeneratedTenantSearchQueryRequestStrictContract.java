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
import java.util.ArrayList;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedTenantSearchQueryRequestStrictContract(
    @Nullable java.util.List<GeneratedTenantSearchQuerySortRequestStrictContract> sort,
    @Nullable GeneratedTenantFilterStrictContract filter) {

  public static java.util.List<GeneratedTenantSearchQuerySortRequestStrictContract> coerceSort(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedTenantSearchQuerySortRequestStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedTenantSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedTenantSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedTenantSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static GeneratedTenantFilterStrictContract coerceFilter(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedTenantFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedTenantFilterStrictContract, but was "
            + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object sort;
    private Object filter;

    private Builder() {}

    @Override
    public OptionalStep sort(
        final java.util.List<GeneratedTenantSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(
        final java.util.List<GeneratedTenantSearchQuerySortRequestStrictContract> sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedTenantSearchQuerySortRequestStrictContract>>
            policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep sort(final Object sort, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep filter(final GeneratedTenantFilterStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(
        final GeneratedTenantFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedTenantFilterStrictContract> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public OptionalStep filter(
        final Object filter, final ContractPolicy.FieldPolicy<Object> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public GeneratedTenantSearchQueryRequestStrictContract build() {
      return new GeneratedTenantSearchQueryRequestStrictContract(
          coerceSort(this.sort), coerceFilter(this.filter));
    }
  }

  public interface OptionalStep {
    OptionalStep sort(
        final java.util.List<GeneratedTenantSearchQuerySortRequestStrictContract> sort);

    OptionalStep sort(final Object sort);

    OptionalStep sort(
        final java.util.List<GeneratedTenantSearchQuerySortRequestStrictContract> sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedTenantSearchQuerySortRequestStrictContract>>
            policy);

    OptionalStep sort(final Object sort, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep filter(final GeneratedTenantFilterStrictContract filter);

    OptionalStep filter(final Object filter);

    OptionalStep filter(
        final GeneratedTenantFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedTenantFilterStrictContract> policy);

    OptionalStep filter(final Object filter, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedTenantSearchQueryRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SORT =
        ContractPolicy.field("TenantSearchQueryRequest", "sort");
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("TenantSearchQueryRequest", "filter");

    private Fields() {}
  }
}
