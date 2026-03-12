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
public record GeneratedSearchQueryPageResponseStrictContract(
    Long totalItems,
    Boolean hasMoreTotalItems,
    @Nullable String startCursor,
    @Nullable String endCursor) {

  public GeneratedSearchQueryPageResponseStrictContract {
    Objects.requireNonNull(totalItems, "totalItems is required and must not be null");
    Objects.requireNonNull(hasMoreTotalItems, "hasMoreTotalItems is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static TotalItemsStep builder() {
    return new Builder();
  }

  public static final class Builder implements TotalItemsStep, HasMoreTotalItemsStep, OptionalStep {
    private Long totalItems;
    private ContractPolicy.FieldPolicy<Long> totalItemsPolicy;
    private Boolean hasMoreTotalItems;
    private ContractPolicy.FieldPolicy<Boolean> hasMoreTotalItemsPolicy;
    private String startCursor;
    private String endCursor;

    private Builder() {}

    @Override
    public HasMoreTotalItemsStep totalItems(
        final Long totalItems, final ContractPolicy.FieldPolicy<Long> policy) {
      this.totalItems = totalItems;
      this.totalItemsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep hasMoreTotalItems(
        final Boolean hasMoreTotalItems, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasMoreTotalItems = hasMoreTotalItems;
      this.hasMoreTotalItemsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep startCursor(final String startCursor) {
      this.startCursor = startCursor;
      return this;
    }

    @Override
    public OptionalStep startCursor(
        final String startCursor, final ContractPolicy.FieldPolicy<String> policy) {
      this.startCursor = policy.apply(startCursor, Fields.START_CURSOR, null);
      return this;
    }

    @Override
    public OptionalStep endCursor(final String endCursor) {
      this.endCursor = endCursor;
      return this;
    }

    @Override
    public OptionalStep endCursor(
        final String endCursor, final ContractPolicy.FieldPolicy<String> policy) {
      this.endCursor = policy.apply(endCursor, Fields.END_CURSOR, null);
      return this;
    }

    @Override
    public GeneratedSearchQueryPageResponseStrictContract build() {
      return new GeneratedSearchQueryPageResponseStrictContract(
          applyRequiredPolicy(this.totalItems, this.totalItemsPolicy, Fields.TOTAL_ITEMS),
          applyRequiredPolicy(
              this.hasMoreTotalItems, this.hasMoreTotalItemsPolicy, Fields.HAS_MORE_TOTAL_ITEMS),
          this.startCursor,
          this.endCursor);
    }
  }

  public interface TotalItemsStep {
    HasMoreTotalItemsStep totalItems(
        final Long totalItems, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface HasMoreTotalItemsStep {
    OptionalStep hasMoreTotalItems(
        final Boolean hasMoreTotalItems, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface OptionalStep {
    OptionalStep startCursor(final String startCursor);

    OptionalStep startCursor(
        final String startCursor, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep endCursor(final String endCursor);

    OptionalStep endCursor(final String endCursor, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedSearchQueryPageResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TOTAL_ITEMS =
        ContractPolicy.field("SearchQueryPageResponse", "totalItems");
    public static final ContractPolicy.FieldRef HAS_MORE_TOTAL_ITEMS =
        ContractPolicy.field("SearchQueryPageResponse", "hasMoreTotalItems");
    public static final ContractPolicy.FieldRef START_CURSOR =
        ContractPolicy.field("SearchQueryPageResponse", "startCursor");
    public static final ContractPolicy.FieldRef END_CURSOR =
        ContractPolicy.field("SearchQueryPageResponse", "endCursor");

    private Fields() {}
  }
}
