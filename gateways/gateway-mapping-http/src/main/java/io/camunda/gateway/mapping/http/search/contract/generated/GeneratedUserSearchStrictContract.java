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
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserSearchStrictContract(java.util.List<GeneratedUserStrictContract> items) {

  public GeneratedUserSearchStrictContract {
    Objects.requireNonNull(items, "items is required and must not be null");
  }

  public static java.util.List<GeneratedUserStrictContract> coerceItems(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "items must be a List of GeneratedUserStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedUserStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedUserStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "items must contain only GeneratedUserStrictContract items, but got "
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
    public GeneratedUserSearchStrictContract build() {
      return new GeneratedUserSearchStrictContract(coerceItems(this.items));
    }
  }

  public interface ItemsStep {
    OptionalStep items(final Object items);
  }

  public interface OptionalStep {
    GeneratedUserSearchStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ITEMS =
        ContractPolicy.field("UserSearchResult", "items");

    private Fields() {}
  }
}
