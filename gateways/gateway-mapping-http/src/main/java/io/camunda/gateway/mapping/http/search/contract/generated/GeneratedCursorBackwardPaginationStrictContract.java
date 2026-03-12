/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedCursorBackwardPaginationStrictContract(
    String before, @Nullable Integer limit) {

  public GeneratedCursorBackwardPaginationStrictContract {
    Objects.requireNonNull(before, "before is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static BeforeStep builder() {
    return new Builder();
  }

  public static final class Builder implements BeforeStep, OptionalStep {
    private String before;
    private ContractPolicy.FieldPolicy<String> beforePolicy;
    private Integer limit;

    private Builder() {}

    @Override
    public OptionalStep before(
        final String before, final ContractPolicy.FieldPolicy<String> policy) {
      this.before = before;
      this.beforePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep limit(final Integer limit) {
      this.limit = limit;
      return this;
    }

    @Override
    public OptionalStep limit(
        final Integer limit, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.limit = policy.apply(limit, Fields.LIMIT, null);
      return this;
    }

    @Override
    public GeneratedCursorBackwardPaginationStrictContract build() {
      return new GeneratedCursorBackwardPaginationStrictContract(
          applyRequiredPolicy(this.before, this.beforePolicy, Fields.BEFORE), this.limit);
    }
  }

  public interface BeforeStep {
    OptionalStep before(final String before, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep limit(final Integer limit);

    OptionalStep limit(final Integer limit, final ContractPolicy.FieldPolicy<Integer> policy);

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
