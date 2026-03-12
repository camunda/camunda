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
public record GeneratedRoleGroupSearchQueryRequestStrictContract(
    @Nullable java.util.List<GeneratedRoleGroupSearchQuerySortRequestStrictContract> sort) {

  public static java.util.List<GeneratedRoleGroupSearchQuerySortRequestStrictContract> coerceSort(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedRoleGroupSearchQuerySortRequestStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedRoleGroupSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedRoleGroupSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedRoleGroupSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
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

    private Builder() {}

    @Override
    public OptionalStep sort(
        final java.util.List<GeneratedRoleGroupSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(
        final java.util.List<GeneratedRoleGroupSearchQuerySortRequestStrictContract> sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedRoleGroupSearchQuerySortRequestStrictContract>>
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
    public GeneratedRoleGroupSearchQueryRequestStrictContract build() {
      return new GeneratedRoleGroupSearchQueryRequestStrictContract(coerceSort(this.sort));
    }
  }

  public interface OptionalStep {
    OptionalStep sort(
        final java.util.List<GeneratedRoleGroupSearchQuerySortRequestStrictContract> sort);

    OptionalStep sort(final Object sort);

    OptionalStep sort(
        final java.util.List<GeneratedRoleGroupSearchQuerySortRequestStrictContract> sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedRoleGroupSearchQuerySortRequestStrictContract>>
            policy);

    OptionalStep sort(final Object sort, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedRoleGroupSearchQueryRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SORT =
        ContractPolicy.field("RoleGroupSearchQueryRequest", "sort");

    private Fields() {}
  }
}
