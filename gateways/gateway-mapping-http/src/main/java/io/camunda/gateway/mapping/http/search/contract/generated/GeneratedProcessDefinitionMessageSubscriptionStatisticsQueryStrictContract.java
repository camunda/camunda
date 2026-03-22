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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract(
    @JsonProperty("page") @Nullable GeneratedCursorForwardPaginationStrictContract page,
    @JsonProperty("filter") @Nullable GeneratedMessageSubscriptionFilterStrictContract filter) {

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

  public static GeneratedMessageSubscriptionFilterStrictContract coerceFilter(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedMessageSubscriptionFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedMessageSubscriptionFilterStrictContract, but was "
            + value.getClass().getName());
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object page;
    private Object filter;

    private Builder() {}

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
    public OptionalStep filter(
        final @Nullable GeneratedMessageSubscriptionFilterStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(
        final @Nullable GeneratedMessageSubscriptionFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedMessageSubscriptionFilterStrictContract> policy) {
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
    public GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract build() {
      return new GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract(
          coercePage(this.page), coerceFilter(this.filter));
    }
  }

  public interface OptionalStep {
    OptionalStep page(final @Nullable GeneratedCursorForwardPaginationStrictContract page);

    OptionalStep page(final @Nullable Object page);

    OptionalStep page(
        final @Nullable GeneratedCursorForwardPaginationStrictContract page,
        final ContractPolicy.FieldPolicy<GeneratedCursorForwardPaginationStrictContract> policy);

    OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep filter(final @Nullable GeneratedMessageSubscriptionFilterStrictContract filter);

    OptionalStep filter(final @Nullable Object filter);

    OptionalStep filter(
        final @Nullable GeneratedMessageSubscriptionFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedMessageSubscriptionFilterStrictContract> policy);

    OptionalStep filter(
        final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE =
        ContractPolicy.field("ProcessDefinitionMessageSubscriptionStatisticsQuery", "page");
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("ProcessDefinitionMessageSubscriptionStatisticsQuery", "filter");

    private Fields() {}
  }
}
