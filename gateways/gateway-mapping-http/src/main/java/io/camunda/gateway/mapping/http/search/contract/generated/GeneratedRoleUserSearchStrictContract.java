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
public record GeneratedRoleUserSearchStrictContract(
    java.util.List<GeneratedRoleUserStrictContract> items) {

  public GeneratedRoleUserSearchStrictContract {
    Objects.requireNonNull(items, "items is required and must not be null");
  }

  public static java.util.List<GeneratedRoleUserStrictContract> coerceItems(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "items must be a List of GeneratedRoleUserStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedRoleUserStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedRoleUserStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "items must contain only GeneratedRoleUserStrictContract items, but got "
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
    public GeneratedRoleUserSearchStrictContract build() {
      return new GeneratedRoleUserSearchStrictContract(coerceItems(this.items));
    }
  }

  public interface ItemsStep {
    OptionalStep items(final Object items);
  }

  public interface OptionalStep {
    GeneratedRoleUserSearchStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ITEMS =
        ContractPolicy.field("RoleUserSearchResult", "items");

    private Fields() {}
  }
}
