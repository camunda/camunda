/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedCursorForwardPaginationStrictContract(
    String after, @Nullable Integer limit) {

  public GeneratedCursorForwardPaginationStrictContract {
    Objects.requireNonNull(after, "after is required and must not be null");
  }

  public static AfterStep builder() {
    return new Builder();
  }

  public static final class Builder implements AfterStep, OptionalStep {
    private String after;
    private Integer limit;

    private Builder() {}

    @Override
    public OptionalStep after(final String after) {
      this.after = after;
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

  public interface AfterStep {
    OptionalStep after(final String after);
  }

  public interface OptionalStep {
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
