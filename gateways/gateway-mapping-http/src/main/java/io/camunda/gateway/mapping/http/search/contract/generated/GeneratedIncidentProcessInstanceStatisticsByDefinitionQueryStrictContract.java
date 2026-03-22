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
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract(
    @JsonProperty("filter")
        GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract filter,
    @JsonProperty("page") @Nullable GeneratedOffsetPaginationStrictContract page,
    @JsonProperty("sort")
        java.util.@Nullable List<
                GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>
            sort) {

  public GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract {
    Objects.requireNonNull(filter, "No filter provided.");
  }

  public static GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract
      coerceFilter(final Object value) {
    if (value == null) {
      return null;
    }
    if (value
        instanceof
        GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract, but was "
            + value.getClass().getName());
  }

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
          GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>
      coerceSort(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<
            GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof
          GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract
              strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static FilterStep builder() {
    return new Builder();
  }

  public static final class Builder implements FilterStep, OptionalStep {
    private Object filter;
    private Object page;
    private Object sort;

    private Builder() {}

    @Override
    public OptionalStep filter(final Object filter) {
      this.filter = filter;
      return this;
    }

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
                GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>
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
                GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>
            sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>>
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
    public GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract build() {
      return new GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract(
          coerceFilter(this.filter), coercePage(this.page), coerceSort(this.sort));
    }
  }

  public interface FilterStep {
    OptionalStep filter(final Object filter);
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
                GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>
            sort);

    OptionalStep sort(final @Nullable Object sort);

    OptionalStep sort(
        final java.util.@Nullable List<
                GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>
            sort,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    GeneratedIncidentProcessInstanceStatisticsByDefinitionQuerySortRequestStrictContract>>
            policy);

    OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByDefinitionQuery", "filter");
    public static final ContractPolicy.FieldRef PAGE =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByDefinitionQuery", "page");
    public static final ContractPolicy.FieldRef SORT =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByDefinitionQuery", "sort");

    private Fields() {}
  }
}
