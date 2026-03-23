/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/search-models.yaml#/components/schemas/CursorForwardPagination
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
public record GeneratedCursorForwardPaginationStrictContract(
    @JsonProperty("after") @Nullable String after, @JsonProperty("limit") @Nullable Integer limit) {

  public GeneratedCursorForwardPaginationStrictContract {
    if (after != null)
      if (!after.matches("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=)?$"))
        throw new IllegalArgumentException(
            "The provided after contains illegal characters. It must match the pattern '^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=)?$'.");
    if (limit != null)
      if (limit < 1)
        throw new IllegalArgumentException(
            "The value for limit is '" + limit + "' but must be > 0.");
    if (limit != null)
      if (limit > 10000)
        throw new IllegalArgumentException(
            "The value for limit is '" + limit + "' but must be at most 10000.");
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String after;
    private Integer limit;

    private Builder() {}

    @Override
    public OptionalStep after(final @Nullable String after) {
      this.after = after;
      return this;
    }

    @Override
    public OptionalStep after(
        final @Nullable String after, final ContractPolicy.FieldPolicy<String> policy) {
      this.after = policy.apply(after, Fields.AFTER, null);
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
    public GeneratedCursorForwardPaginationStrictContract build() {
      return new GeneratedCursorForwardPaginationStrictContract(this.after, this.limit);
    }
  }

  public interface OptionalStep {
    OptionalStep after(final @Nullable String after);

    OptionalStep after(
        final @Nullable String after, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep limit(final @Nullable Integer limit);

    OptionalStep limit(
        final @Nullable Integer limit, final ContractPolicy.FieldPolicy<Integer> policy);

    GeneratedCursorForwardPaginationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef AFTER =
        ContractPolicy.field("CursorForwardPagination", "after");
    public static final ContractPolicy.FieldRef LIMIT =
        ContractPolicy.field("CursorForwardPagination", "limit");

    private Fields() {}
  }
}
