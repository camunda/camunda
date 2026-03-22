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
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobTimeSeriesStatisticsQueryStrictContract(
    @JsonProperty("filter") GeneratedJobTimeSeriesStatisticsFilterStrictContract filter,
    @JsonProperty("page") @Nullable GeneratedCursorForwardPaginationStrictContract page) {

  public GeneratedJobTimeSeriesStatisticsQueryStrictContract {
    Objects.requireNonNull(filter, "No filter provided.");
  }

  public static GeneratedJobTimeSeriesStatisticsFilterStrictContract coerceFilter(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedJobTimeSeriesStatisticsFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedJobTimeSeriesStatisticsFilterStrictContract, but was "
            + value.getClass().getName());
  }

  public static GeneratedCursorForwardPaginationStrictContract coercePage(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedCursorForwardPaginationStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "page must be a GeneratedCursorForwardPaginationStrictContract, but was "
            + value.getClass().getName());
  }

  public static FilterStep builder() {
    return new Builder();
  }

  public static final class Builder implements FilterStep, OptionalStep {
    private Object filter;
    private Object page;

    private Builder() {}

    @Override
    public OptionalStep filter(final Object filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep page(final @Nullable GeneratedCursorForwardPaginationStrictContract page) {
      this.page = page;
      return this;
    }

    @Override
    public OptionalStep page(final @Nullable Object page) {
      this.page = page;
      return this;
    }

    public Builder page(
        final @Nullable GeneratedCursorForwardPaginationStrictContract page,
        final ContractPolicy.FieldPolicy<GeneratedCursorForwardPaginationStrictContract> policy) {
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
    public GeneratedJobTimeSeriesStatisticsQueryStrictContract build() {
      return new GeneratedJobTimeSeriesStatisticsQueryStrictContract(
          coerceFilter(this.filter), coercePage(this.page));
    }
  }

  public interface FilterStep {
    OptionalStep filter(final Object filter);
  }

  public interface OptionalStep {
    OptionalStep page(final @Nullable GeneratedCursorForwardPaginationStrictContract page);

    OptionalStep page(final @Nullable Object page);

    OptionalStep page(
        final @Nullable GeneratedCursorForwardPaginationStrictContract page,
        final ContractPolicy.FieldPolicy<GeneratedCursorForwardPaginationStrictContract> policy);

    OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedJobTimeSeriesStatisticsQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("JobTimeSeriesStatisticsQuery", "filter");
    public static final ContractPolicy.FieldRef PAGE =
        ContractPolicy.field("JobTimeSeriesStatisticsQuery", "page");

    private Fields() {}
  }
}
