/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/incidents.yaml#/components/schemas/IncidentProcessInstanceStatisticsByErrorQuery
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract(
    @JsonProperty("page") @Nullable GeneratedOffsetPaginationStrictContract page,
    @JsonProperty("sort")
        java.util.@Nullable List<
                GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>
            sort) {

  public static GeneratedOffsetPaginationStrictContract coercePage(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedOffsetPaginationStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "page must be a GeneratedOffsetPaginationStrictContract, but was "
            + value.getClass().getName());
  }

  public static java.util.List<
          GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>
      coerceSort(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<
            GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof
          GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract
              strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract items, but got "
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
    public OptionalStep page(final @Nullable GeneratedOffsetPaginationStrictContract page) {
      this.page = page;
      return this;
    }

    @Override
    public OptionalStep page(final @Nullable Object page) {
      this.page = page;
      return this;
    }

    public Builder page(
        final @Nullable GeneratedOffsetPaginationStrictContract page,
        final ContractPolicy.FieldPolicy<GeneratedOffsetPaginationStrictContract> policy) {
      this.page = policy.apply(page, Fields.PAGE, null);
      return this;
    }

    @Override
    public OptionalStep page(
        final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy) {
      this.page = policy.apply(page, Fields.PAGE, null);
      return this;
    }

    @Override
    public OptionalStep sort(
        final java.util.@Nullable List<
                GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>
            sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(
        final java.util.@Nullable List<
                GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>
            sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>>
            policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep sort(
        final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract build() {
      return new GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract(
          coercePage(this.page), coerceSort(this.sort));
    }
  }

  public interface OptionalStep {
    OptionalStep page(final @Nullable GeneratedOffsetPaginationStrictContract page);

    OptionalStep page(final @Nullable Object page);

    OptionalStep page(
        final @Nullable GeneratedOffsetPaginationStrictContract page,
        final ContractPolicy.FieldPolicy<GeneratedOffsetPaginationStrictContract> policy);

    OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep sort(
        final java.util.@Nullable List<
                GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>
            sort);

    OptionalStep sort(final @Nullable Object sort);

    OptionalStep sort(
        final java.util.@Nullable List<
                GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>
            sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    GeneratedIncidentProcessInstanceStatisticsByErrorQuerySortRequestStrictContract>>
            policy);

    OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByErrorQuery", "page");
    public static final ContractPolicy.FieldRef SORT =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByErrorQuery", "sort");

    private Fields() {}
  }
}
