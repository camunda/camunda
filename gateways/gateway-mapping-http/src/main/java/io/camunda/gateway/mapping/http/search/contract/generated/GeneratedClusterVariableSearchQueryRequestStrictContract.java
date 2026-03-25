/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/cluster-variables.yaml#/components/schemas/ClusterVariableSearchQueryRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedClusterVariableSearchQueryRequestStrictContract(
    @Nullable Object page,
    java.util.@Nullable List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort,
    @Nullable GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter
) {

  public static java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> coerceSort(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedClusterVariableSearchQuerySortRequestStrictContract, but was " + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedClusterVariableSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedClusterVariableSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedClusterVariableSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }


  public static GeneratedClusterVariableSearchQueryFilterRequestStrictContract coerceFilter(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedClusterVariableSearchQueryFilterRequestStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedClusterVariableSearchQueryFilterRequestStrictContract, but was " + value.getClass().getName());
  }



  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object page;
    private Object sort;
    private Object filter;

    private Builder() {}

    @Override
    public OptionalStep page(final @Nullable Object page) {
      this.page = page;
      return this;
    }

    @Override
    public OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy) {
      this.page = policy.apply(page, Fields.PAGE, null);
      return this;
    }


    @Override
    public OptionalStep sort(final java.util.@Nullable List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(final java.util.@Nullable List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract>> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }


    @Override
    public OptionalStep filter(final @Nullable GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(final @Nullable GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter, final ContractPolicy.FieldPolicy<GeneratedClusterVariableSearchQueryFilterRequestStrictContract> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public GeneratedClusterVariableSearchQueryRequestStrictContract build() {
      return new GeneratedClusterVariableSearchQueryRequestStrictContract(
          this.page,
          coerceSort(this.sort),
          coerceFilter(this.filter));
    }
  }

  public interface OptionalStep {
  OptionalStep page(final @Nullable Object page);

  OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep sort(final java.util.@Nullable List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort);

  OptionalStep sort(final @Nullable Object sort);

  OptionalStep sort(final java.util.@Nullable List<GeneratedClusterVariableSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedClusterVariableSearchQuerySortRequestStrictContract>> policy);

  OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep filter(final @Nullable GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter);

  OptionalStep filter(final @Nullable Object filter);

  OptionalStep filter(final @Nullable GeneratedClusterVariableSearchQueryFilterRequestStrictContract filter, final ContractPolicy.FieldPolicy<GeneratedClusterVariableSearchQueryFilterRequestStrictContract> policy);

  OptionalStep filter(final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedClusterVariableSearchQueryRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE = ContractPolicy.field("ClusterVariableSearchQueryRequest", "page");
    public static final ContractPolicy.FieldRef SORT = ContractPolicy.field("ClusterVariableSearchQueryRequest", "sort");
    public static final ContractPolicy.FieldRef FILTER = ContractPolicy.field("ClusterVariableSearchQueryRequest", "filter");

    private Fields() {}
  }


}
