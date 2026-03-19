/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/job-metrics.yaml#/components/schemas/JobTypeStatisticsQuery
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobTypeStatisticsQueryStrictContract(
    @JsonProperty("filter") @Nullable GeneratedJobTypeStatisticsFilterStrictContract filter,
    @JsonProperty("page") @Nullable GeneratedCursorForwardPaginationStrictContract page) {

  public static GeneratedJobTypeStatisticsFilterStrictContract coerceFilter(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedJobTypeStatisticsFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedJobTypeStatisticsFilterStrictContract, but was "
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

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object filter;
    private Object page;

    private Builder() {}

    @Override
    public OptionalStep filter(
        final @Nullable GeneratedJobTypeStatisticsFilterStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(
        final @Nullable GeneratedJobTypeStatisticsFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedJobTypeStatisticsFilterStrictContract> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public OptionalStep filter(
        final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
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
    public GeneratedJobTypeStatisticsQueryStrictContract build() {
      return new GeneratedJobTypeStatisticsQueryStrictContract(
          coerceFilter(this.filter), coercePage(this.page));
    }
  }

  public interface OptionalStep {
    OptionalStep filter(final @Nullable GeneratedJobTypeStatisticsFilterStrictContract filter);

    OptionalStep filter(final @Nullable Object filter);

    OptionalStep filter(
        final @Nullable GeneratedJobTypeStatisticsFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedJobTypeStatisticsFilterStrictContract> policy);

    OptionalStep filter(
        final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep page(final @Nullable GeneratedCursorForwardPaginationStrictContract page);

    OptionalStep page(final @Nullable Object page);

    OptionalStep page(
        final @Nullable GeneratedCursorForwardPaginationStrictContract page,
        final ContractPolicy.FieldPolicy<GeneratedCursorForwardPaginationStrictContract> policy);

    OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedJobTypeStatisticsQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("JobTypeStatisticsQuery", "filter");
    public static final ContractPolicy.FieldRef PAGE =
        ContractPolicy.field("JobTypeStatisticsQuery", "page");

    private Fields() {}
  }
}
