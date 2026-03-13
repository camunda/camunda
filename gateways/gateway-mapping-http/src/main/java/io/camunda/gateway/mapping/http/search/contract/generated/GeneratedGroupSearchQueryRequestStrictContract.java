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
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGroupSearchQueryRequestStrictContract(
    java.util.@Nullable List<GeneratedGroupSearchQuerySortRequestStrictContract> sort,
    @Nullable GeneratedGroupFilterStrictContract filter) {

  public static java.util.List<GeneratedGroupSearchQuerySortRequestStrictContract> coerceSort(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedGroupSearchQuerySortRequestStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedGroupSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedGroupSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedGroupSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static GeneratedGroupFilterStrictContract coerceFilter(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedGroupFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedGroupFilterStrictContract, but was "
            + value.getClass().getName());
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
        final java.util.@Nullable List<GeneratedGroupSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(
        final java.util.@Nullable List<GeneratedGroupSearchQuerySortRequestStrictContract> sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedGroupSearchQuerySortRequestStrictContract>>
            policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep sort(
        final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable GeneratedGroupFilterStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(
        final @Nullable GeneratedGroupFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedGroupFilterStrictContract> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public OptionalStep filter(
        final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public GeneratedGroupSearchQueryRequestStrictContract build() {
      return new GeneratedGroupSearchQueryRequestStrictContract(
          coerceSort(this.sort), coerceFilter(this.filter));
    }
  }

  public interface OptionalStep {
    OptionalStep sort(
        final java.util.@Nullable List<GeneratedGroupSearchQuerySortRequestStrictContract> sort);

    OptionalStep sort(final @Nullable Object sort);

    OptionalStep sort(
        final java.util.@Nullable List<GeneratedGroupSearchQuerySortRequestStrictContract> sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedGroupSearchQuerySortRequestStrictContract>>
            policy);

    OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep filter(final @Nullable GeneratedGroupFilterStrictContract filter);

    OptionalStep filter(final @Nullable Object filter);

    OptionalStep filter(
        final @Nullable GeneratedGroupFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedGroupFilterStrictContract> policy);

    OptionalStep filter(
        final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedGroupSearchQueryRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SORT =
        ContractPolicy.field("GroupSearchQueryRequest", "sort");
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("GroupSearchQueryRequest", "filter");

    private Fields() {}
  }
}
