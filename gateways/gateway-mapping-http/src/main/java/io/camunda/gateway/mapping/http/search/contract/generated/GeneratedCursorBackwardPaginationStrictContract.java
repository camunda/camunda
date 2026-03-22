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
public record GeneratedCursorBackwardPaginationStrictContract(
    @JsonProperty("before") String before, @JsonProperty("limit") @Nullable Integer limit) {

  public GeneratedCursorBackwardPaginationStrictContract {
    Objects.requireNonNull(before, "No before provided.");
    if (!before.matches("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=)?$"))
      throw new IllegalArgumentException(
          "The provided before contains illegal characters. It must match the pattern '^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=)?$'.");
    if (limit != null)
      if (limit < 1)
        throw new IllegalArgumentException(
            "The value for limit is '" + limit + "' but must be > 0.");
    if (limit != null)
      if (limit > 10000)
        throw new IllegalArgumentException(
            "The value for limit is '" + limit + "' but must be at most 10000.");
  }

  public static BeforeStep builder() {
    return new Builder();
  }

  public static final class Builder implements BeforeStep, OptionalStep {
    private String before;
    private Integer limit;

    private Builder() {}

    @Override
    public OptionalStep before(final String before) {
      this.before = before;
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
    public GeneratedCursorBackwardPaginationStrictContract build() {
      return new GeneratedCursorBackwardPaginationStrictContract(this.before, this.limit);
    }
  }

  public interface BeforeStep {
    OptionalStep before(final String before);
  }

  public interface OptionalStep {
    OptionalStep limit(final @Nullable Integer limit);

    OptionalStep limit(
        final @Nullable Integer limit, final ContractPolicy.FieldPolicy<Integer> policy);

    GeneratedCursorBackwardPaginationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef BEFORE =
        ContractPolicy.field("CursorBackwardPagination", "before");
    public static final ContractPolicy.FieldRef LIMIT =
        ContractPolicy.field("CursorBackwardPagination", "limit");

    private Fields() {}
  }
}
