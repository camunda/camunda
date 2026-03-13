/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/tenants.yaml#/components/schemas/TenantGroupSearchQueryRequest
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
public record GeneratedTenantGroupSearchQueryRequestStrictContract(
    @Nullable Object page,
    java.util.@Nullable List<GeneratedTenantGroupSearchQuerySortRequestStrictContract> sort
) {

  public static java.util.List<GeneratedTenantGroupSearchQuerySortRequestStrictContract> coerceSort(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedTenantGroupSearchQuerySortRequestStrictContract, but was " + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedTenantGroupSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedTenantGroupSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedTenantGroupSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }



  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object page;
    private Object sort;

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
    public OptionalStep sort(final java.util.@Nullable List<GeneratedTenantGroupSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(final java.util.@Nullable List<GeneratedTenantGroupSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedTenantGroupSearchQuerySortRequestStrictContract>> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public GeneratedTenantGroupSearchQueryRequestStrictContract build() {
      return new GeneratedTenantGroupSearchQueryRequestStrictContract(
          this.page,
          coerceSort(this.sort));
    }
  }

  public interface OptionalStep {
  OptionalStep page(final @Nullable Object page);

  OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep sort(final java.util.@Nullable List<GeneratedTenantGroupSearchQuerySortRequestStrictContract> sort);

  OptionalStep sort(final @Nullable Object sort);

  OptionalStep sort(final java.util.@Nullable List<GeneratedTenantGroupSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedTenantGroupSearchQuerySortRequestStrictContract>> policy);

  OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedTenantGroupSearchQueryRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE = ContractPolicy.field("TenantGroupSearchQueryRequest", "page");
    public static final ContractPolicy.FieldRef SORT = ContractPolicy.field("TenantGroupSearchQueryRequest", "sort");

    private Fields() {}
  }


}
