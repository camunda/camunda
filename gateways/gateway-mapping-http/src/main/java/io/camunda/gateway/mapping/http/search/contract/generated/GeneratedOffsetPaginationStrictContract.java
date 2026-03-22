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
public record GeneratedOffsetPaginationStrictContract(
    @JsonProperty("from") @Nullable Integer from, @JsonProperty("limit") @Nullable Integer limit) {

  public GeneratedOffsetPaginationStrictContract {
    if (from != null)
      if (from < 0)
        throw new IllegalArgumentException(
            "The value for from is '" + from + "' but must be not negative.");
    if (limit != null)
      if (limit < 1)
        throw new IllegalArgumentException(
            "The value for limit is '" + limit + "' but must be > 0.");
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Integer from;
    private Integer limit;

    private Builder() {}

    @Override
    public OptionalStep from(final @Nullable Integer from) {
      this.from = from;
      return this;
    }

    @Override
    public OptionalStep from(
        final @Nullable Integer from, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.from = policy.apply(from, Fields.FROM, null);
      return this;
    }

    @Override
    public OptionalStep limit(final @Nullable Integer limit) {
      this.limit = limit;
      return this;
    }

    @Override
    public OptionalStep limit(
        final @Nullable Integer limit, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.limit = policy.apply(limit, Fields.LIMIT, null);
      return this;
    }

    @Override
    public GeneratedOffsetPaginationStrictContract build() {
      return new GeneratedOffsetPaginationStrictContract(this.from, this.limit);
    }
  }

  public interface OptionalStep {
    OptionalStep from(final @Nullable Integer from);

    OptionalStep from(
        final @Nullable Integer from, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep limit(final @Nullable Integer limit);

    OptionalStep limit(
        final @Nullable Integer limit, final ContractPolicy.FieldPolicy<Integer> policy);

    GeneratedOffsetPaginationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FROM =
        ContractPolicy.field("OffsetPagination", "from");
    public static final ContractPolicy.FieldRef LIMIT =
        ContractPolicy.field("OffsetPagination", "limit");

    private Fields() {}
  }
}
