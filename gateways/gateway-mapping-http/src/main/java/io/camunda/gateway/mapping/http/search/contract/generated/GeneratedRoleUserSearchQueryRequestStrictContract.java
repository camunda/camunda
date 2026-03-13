/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/roles.yaml#/components/schemas/RoleUserSearchQueryRequest
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
public record GeneratedRoleUserSearchQueryRequestStrictContract(
    @Nullable Object page,
    java.util.@Nullable List<GeneratedRoleUserSearchQuerySortRequestStrictContract> sort
) {

  public static java.util.List<GeneratedRoleUserSearchQuerySortRequestStrictContract> coerceSort(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedRoleUserSearchQuerySortRequestStrictContract, but was " + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedRoleUserSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedRoleUserSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedRoleUserSearchQuerySortRequestStrictContract items, but got "
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
    public OptionalStep sort(final java.util.@Nullable List<GeneratedRoleUserSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(final java.util.@Nullable List<GeneratedRoleUserSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedRoleUserSearchQuerySortRequestStrictContract>> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public GeneratedRoleUserSearchQueryRequestStrictContract build() {
      return new GeneratedRoleUserSearchQueryRequestStrictContract(
          this.page,
          coerceSort(this.sort));
    }
  }

  public interface OptionalStep {
  OptionalStep page(final @Nullable Object page);

  OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep sort(final java.util.@Nullable List<GeneratedRoleUserSearchQuerySortRequestStrictContract> sort);

  OptionalStep sort(final @Nullable Object sort);

  OptionalStep sort(final java.util.@Nullable List<GeneratedRoleUserSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedRoleUserSearchQuerySortRequestStrictContract>> policy);

  OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedRoleUserSearchQueryRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE = ContractPolicy.field("RoleUserSearchQueryRequest", "page");
    public static final ContractPolicy.FieldRef SORT = ContractPolicy.field("RoleUserSearchQueryRequest", "sort");

    private Fields() {}
  }


}
