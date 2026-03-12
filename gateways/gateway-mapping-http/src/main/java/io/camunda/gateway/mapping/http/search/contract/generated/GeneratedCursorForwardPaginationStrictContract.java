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
public record GeneratedCursorForwardPaginationStrictContract(
    String after, @Nullable Integer limit) {

  public GeneratedCursorForwardPaginationStrictContract {
    Objects.requireNonNull(after, "after is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static AfterStep builder() {
    return new Builder();
  }

  public static final class Builder implements AfterStep, OptionalStep {
    private String after;
    private ContractPolicy.FieldPolicy<String> afterPolicy;
    private Integer limit;

    private Builder() {}

    @Override
    public OptionalStep after(final String after, final ContractPolicy.FieldPolicy<String> policy) {
      this.after = after;
      this.afterPolicy = policy;
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
    public GeneratedCursorForwardPaginationStrictContract build() {
      return new GeneratedCursorForwardPaginationStrictContract(
          applyRequiredPolicy(this.after, this.afterPolicy, Fields.AFTER), this.limit);
    }
  }

  public interface AfterStep {
    OptionalStep after(final String after, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep limit(final Integer limit);

    OptionalStep limit(final Integer limit, final ContractPolicy.FieldPolicy<Integer> policy);

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
