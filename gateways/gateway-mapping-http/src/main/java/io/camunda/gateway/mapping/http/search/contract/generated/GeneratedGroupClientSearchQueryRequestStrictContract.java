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
public record GeneratedGroupClientSearchQueryRequestStrictContract(
    java.util.@Nullable List<GeneratedGroupClientSearchQuerySortRequestStrictContract> sort) {

  public static java.util.List<GeneratedGroupClientSearchQuerySortRequestStrictContract> coerceSort(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedGroupClientSearchQuerySortRequestStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedGroupClientSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedGroupClientSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedGroupClientSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object sort;

    private Builder() {}

    @Override
    public OptionalStep sort(
        final java.util.@Nullable List<GeneratedGroupClientSearchQuerySortRequestStrictContract>
            sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(
        final java.util.@Nullable List<GeneratedGroupClientSearchQuerySortRequestStrictContract>
            sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedGroupClientSearchQuerySortRequestStrictContract>>
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
    public GeneratedGroupClientSearchQueryRequestStrictContract build() {
      return new GeneratedGroupClientSearchQueryRequestStrictContract(coerceSort(this.sort));
    }
  }

  public interface OptionalStep {
    OptionalStep sort(
        final java.util.@Nullable List<GeneratedGroupClientSearchQuerySortRequestStrictContract>
            sort);

    OptionalStep sort(final @Nullable Object sort);

    OptionalStep sort(
        final java.util.@Nullable List<GeneratedGroupClientSearchQuerySortRequestStrictContract>
            sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedGroupClientSearchQuerySortRequestStrictContract>>
            policy);

    OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedGroupClientSearchQueryRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SORT =
        ContractPolicy.field("GroupClientSearchQueryRequest", "sort");

    private Fields() {}
  }
}
