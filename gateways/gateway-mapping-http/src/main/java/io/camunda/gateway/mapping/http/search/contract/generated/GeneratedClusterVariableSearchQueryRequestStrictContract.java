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
public record GeneratedClusterVariableSearchQueryRequestStrictContract(
    @Nullable java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort,
    @Nullable GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter) {

  public static java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract>
      coerceSort(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedClusterVariableSearchQuerySortRequestStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedClusterVariableSearchQuerySortRequestStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedClusterVariableSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedClusterVariableSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static GeneratedClusterVariableSearchQueryFilterRequestStrictContract coerceFilter(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value
        instanceof GeneratedClusterVariableSearchQueryFilterRequestStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedClusterVariableSearchQueryFilterRequestStrictContract, but was "
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
        final java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(
        final java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract>>
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
    public OptionalStep filter(
        final GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(
        final GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter,
        final ContractPolicy.FieldPolicy<
                GeneratedClusterVariableSearchQueryFilterRequestStrictContract>
            policy) {
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
    public GeneratedClusterVariableSearchQueryRequestStrictContract build() {
      return new GeneratedClusterVariableSearchQueryRequestStrictContract(
          coerceSort(this.sort), coerceFilter(this.filter));
    }
  }

  public interface OptionalStep {
    OptionalStep sort(
        final java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort);

    OptionalStep sort(final Object sort);

    OptionalStep sort(
        final java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract>>
            policy);

    OptionalStep sort(final Object sort, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep filter(
        final GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter);

    OptionalStep filter(final Object filter);

    OptionalStep filter(
        final GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter,
        final ContractPolicy.FieldPolicy<
                GeneratedClusterVariableSearchQueryFilterRequestStrictContract>
            policy);

    OptionalStep filter(final Object filter, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedClusterVariableSearchQueryRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SORT =
        ContractPolicy.field("ClusterVariableSearchQueryRequest", "sort");
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("ClusterVariableSearchQueryRequest", "filter");

    private Fields() {}
  }
}
