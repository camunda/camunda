/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/authorizations.yaml#/components/schemas/AuthorizationSearchQuery
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
public record GeneratedAuthorizationSearchQueryStrictContract(
    @Nullable Object page,
    java.util.@Nullable List<GeneratedAuthorizationSearchQuerySortRequestStrictContract> sort,
    @Nullable GeneratedAuthorizationFilterStrictContract filter
) {

  public static java.util.List<GeneratedAuthorizationSearchQuerySortRequestStrictContract> coerceSort(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedAuthorizationSearchQuerySortRequestStrictContract, but was " + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedAuthorizationSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedAuthorizationSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedAuthorizationSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }


  public static GeneratedAuthorizationFilterStrictContract coerceFilter(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedAuthorizationFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedAuthorizationFilterStrictContract, but was " + value.getClass().getName());
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
    public OptionalStep sort(final java.util.@Nullable List<GeneratedAuthorizationSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(final java.util.@Nullable List<GeneratedAuthorizationSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedAuthorizationSearchQuerySortRequestStrictContract>> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }


    @Override
    public OptionalStep filter(final @Nullable GeneratedAuthorizationFilterStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(final @Nullable GeneratedAuthorizationFilterStrictContract filter, final ContractPolicy.FieldPolicy<GeneratedAuthorizationFilterStrictContract> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public GeneratedAuthorizationSearchQueryStrictContract build() {
      return new GeneratedAuthorizationSearchQueryStrictContract(
          this.page,
          coerceSort(this.sort),
          coerceFilter(this.filter));
    }
  }

  public interface OptionalStep {
  OptionalStep page(final @Nullable Object page);

  OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep sort(final java.util.@Nullable List<GeneratedAuthorizationSearchQuerySortRequestStrictContract> sort);

  OptionalStep sort(final @Nullable Object sort);

  OptionalStep sort(final java.util.@Nullable List<GeneratedAuthorizationSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedAuthorizationSearchQuerySortRequestStrictContract>> policy);

  OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep filter(final @Nullable GeneratedAuthorizationFilterStrictContract filter);

  OptionalStep filter(final @Nullable Object filter);

  OptionalStep filter(final @Nullable GeneratedAuthorizationFilterStrictContract filter, final ContractPolicy.FieldPolicy<GeneratedAuthorizationFilterStrictContract> policy);

  OptionalStep filter(final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedAuthorizationSearchQueryStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE = ContractPolicy.field("AuthorizationSearchQuery", "page");
    public static final ContractPolicy.FieldRef SORT = ContractPolicy.field("AuthorizationSearchQuery", "sort");
    public static final ContractPolicy.FieldRef FILTER = ContractPolicy.field("AuthorizationSearchQuery", "filter");

    private Fields() {}
  }


}
