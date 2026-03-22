/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedIncidentSearchQueryStrictContract(
    @JsonProperty("page") GeneratedSearchQueryPageResponseStrictContract page,
    @JsonProperty("items") java.util.List<GeneratedIncidentStrictContract> items) {

  public GeneratedIncidentSearchQueryStrictContract {
    Objects.requireNonNull(page, "No page provided.");
    Objects.requireNonNull(items, "No items provided.");
  }

  public static GeneratedSearchQueryPageResponseStrictContract coercePage(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedSearchQueryPageResponseStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "page must be a GeneratedSearchQueryPageResponseStrictContract, but was "
            + value.getClass().getName());
  }

  public static java.util.List<GeneratedIncidentStrictContract> coerceItems(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "items must be a List of GeneratedIncidentStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedIncidentStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedIncidentStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "items must contain only GeneratedIncidentStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static PageStep builder() {
    return new Builder();
  }

  public static final class Builder implements PageStep, ItemsStep, OptionalStep {
    private Object page;
    private Object items;

    private Builder() {}

    @Override
    public ItemsStep page(final Object page) {
      this.page = page;
      return this;
    }

    @Override
    public OptionalStep items(final Object items) {
      this.items = items;
      return this;
    }

    @Override
    public GeneratedIncidentSearchQueryStrictContract build() {
      return new GeneratedIncidentSearchQueryStrictContract(
          coercePage(this.page), coerceItems(this.items));
    }
  }

  public interface PageStep {
    ItemsStep page(final Object page);
  }

  public interface ItemsStep {
    OptionalStep items(final Object items);
  }

  public interface OptionalStep {
    GeneratedIncidentSearchQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE =
        ContractPolicy.field("IncidentSearchQueryResult", "page");
    public static final ContractPolicy.FieldRef ITEMS =
        ContractPolicy.field("IncidentSearchQueryResult", "items");

    private Fields() {}
  }
}
