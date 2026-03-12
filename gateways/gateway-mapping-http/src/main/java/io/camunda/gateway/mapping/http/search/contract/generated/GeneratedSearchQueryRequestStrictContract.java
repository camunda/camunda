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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSearchQueryRequestStrictContract(@Nullable Object page) {

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object page;

    private Builder() {}

    @Override
    public OptionalStep page(final Object page) {
      this.page = page;
      return this;
    }

    @Override
    public OptionalStep page(final Object page, final ContractPolicy.FieldPolicy<Object> policy) {
      this.page = policy.apply(page, Fields.PAGE, null);
      return this;
    }

    @Override
    public GeneratedSearchQueryRequestStrictContract build() {
      return new GeneratedSearchQueryRequestStrictContract(this.page);
    }
  }

  public interface OptionalStep {
    OptionalStep page(final Object page);

    OptionalStep page(final Object page, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedSearchQueryRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE =
        ContractPolicy.field("SearchQueryRequest", "page");

    private Fields() {}
  }
}
