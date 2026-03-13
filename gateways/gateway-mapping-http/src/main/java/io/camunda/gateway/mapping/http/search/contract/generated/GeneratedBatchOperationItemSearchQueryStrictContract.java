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
import org.jspecify.annotations.NullMarked;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBatchOperationItemSearchQueryStrictContract(
    java.util.List<GeneratedBatchOperationItemResponseStrictContract> items) {

  public GeneratedBatchOperationItemSearchQueryStrictContract {
    Objects.requireNonNull(items, "items is required and must not be null");
  }

  public static java.util.List<GeneratedBatchOperationItemResponseStrictContract> coerceItems(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "items must be a List of GeneratedBatchOperationItemResponseStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedBatchOperationItemResponseStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedBatchOperationItemResponseStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "items must contain only GeneratedBatchOperationItemResponseStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static ItemsStep builder() {
    return new Builder();
  }

  public static final class Builder implements ItemsStep, OptionalStep {
    private Object items;

    private Builder() {}

    @Override
    public OptionalStep items(final Object items) {
      this.items = items;
      return this;
    }

    @Override
    public GeneratedBatchOperationItemSearchQueryStrictContract build() {
      return new GeneratedBatchOperationItemSearchQueryStrictContract(coerceItems(this.items));
    }
  }

  public interface ItemsStep {
    OptionalStep items(final Object items);
  }

  public interface OptionalStep {
    GeneratedBatchOperationItemSearchQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ITEMS =
        ContractPolicy.field("BatchOperationItemSearchQueryResult", "items");

    private Fields() {}
  }
}
