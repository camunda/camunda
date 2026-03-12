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
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedElementInstanceSearchQueryStrictContract(
    java.util.List<GeneratedElementInstanceStrictContract> items) {

  public GeneratedElementInstanceSearchQueryStrictContract {
    Objects.requireNonNull(items, "items is required and must not be null");
  }

  public static java.util.List<GeneratedElementInstanceStrictContract> coerceItems(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "items must be a List of GeneratedElementInstanceStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedElementInstanceStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedElementInstanceStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "items must contain only GeneratedElementInstanceStrictContract items, but got "
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

  public static ItemsStep builder() {
    return new Builder();
  }

  public static final class Builder implements ItemsStep, OptionalStep {
    private Object items;
    private ContractPolicy.FieldPolicy<Object> itemsPolicy;

    private Builder() {}

    @Override
    public OptionalStep items(final Object items, final ContractPolicy.FieldPolicy<Object> policy) {
      this.items = items;
      this.itemsPolicy = policy;
      return this;
    }

    @Override
    public GeneratedElementInstanceSearchQueryStrictContract build() {
      return new GeneratedElementInstanceSearchQueryStrictContract(
          coerceItems(applyRequiredPolicy(this.items, this.itemsPolicy, Fields.ITEMS)));
    }
  }

  public interface ItemsStep {
    OptionalStep items(final Object items, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedElementInstanceSearchQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ITEMS =
        ContractPolicy.field("ElementInstanceSearchQueryResult", "items");

    private Fields() {}
  }
}
