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
public record GeneratedProcessDefinitionElementStatisticsQueryStrictContract(
    @JsonProperty("filter")
        @Nullable GeneratedProcessDefinitionStatisticsFilterStrictContract filter) {

  public static GeneratedProcessDefinitionStatisticsFilterStrictContract coerceFilter(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedProcessDefinitionStatisticsFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedProcessDefinitionStatisticsFilterStrictContract, but was "
            + value.getClass().getName());
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object filter;

    private Builder() {}

    @Override
    public OptionalStep filter(
        final @Nullable GeneratedProcessDefinitionStatisticsFilterStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(
        final @Nullable GeneratedProcessDefinitionStatisticsFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionStatisticsFilterStrictContract>
            policy) {
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
    public GeneratedProcessDefinitionElementStatisticsQueryStrictContract build() {
      return new GeneratedProcessDefinitionElementStatisticsQueryStrictContract(
          coerceFilter(this.filter));
    }
  }

  public interface OptionalStep {
    OptionalStep filter(
        final @Nullable GeneratedProcessDefinitionStatisticsFilterStrictContract filter);

    OptionalStep filter(final @Nullable Object filter);

    OptionalStep filter(
        final @Nullable GeneratedProcessDefinitionStatisticsFilterStrictContract filter,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionStatisticsFilterStrictContract>
            policy);

    OptionalStep filter(
        final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedProcessDefinitionElementStatisticsQueryStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("ProcessDefinitionElementStatisticsQuery", "filter");

    private Fields() {}
  }
}
