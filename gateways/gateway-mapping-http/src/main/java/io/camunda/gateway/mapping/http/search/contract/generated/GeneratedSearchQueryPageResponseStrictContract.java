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
public record GeneratedSearchQueryPageResponseStrictContract(
    @JsonProperty("totalItems") Long totalItems,
    @JsonProperty("hasMoreTotalItems") Boolean hasMoreTotalItems,
    @JsonProperty("startCursor") @Nullable String startCursor,
    @JsonProperty("endCursor") @Nullable String endCursor) {

  public GeneratedSearchQueryPageResponseStrictContract {
    Objects.requireNonNull(totalItems, "No totalItems provided.");
    Objects.requireNonNull(hasMoreTotalItems, "No hasMoreTotalItems provided.");
  }

  public static TotalItemsStep builder() {
    return new Builder();
  }

  public static final class Builder implements TotalItemsStep, HasMoreTotalItemsStep, OptionalStep {
    private Long totalItems;
    private Boolean hasMoreTotalItems;
    private String startCursor;
    private String endCursor;

    private Builder() {}

    @Override
    public HasMoreTotalItemsStep totalItems(final Long totalItems) {
      this.totalItems = totalItems;
      return this;
    }

    @Override
    public OptionalStep hasMoreTotalItems(final Boolean hasMoreTotalItems) {
      this.hasMoreTotalItems = hasMoreTotalItems;
      return this;
    }

    @Override
    public OptionalStep startCursor(final @Nullable String startCursor) {
      this.startCursor = startCursor;
      return this;
    }

    @Override
    public OptionalStep startCursor(
        final @Nullable String startCursor, final ContractPolicy.FieldPolicy<String> policy) {
      this.startCursor = policy.apply(startCursor, Fields.START_CURSOR, null);
      return this;
    }

    @Override
    public OptionalStep endCursor(final @Nullable String endCursor) {
      this.endCursor = endCursor;
      return this;
    }

    @Override
    public OptionalStep endCursor(
        final @Nullable String endCursor, final ContractPolicy.FieldPolicy<String> policy) {
      this.endCursor = policy.apply(endCursor, Fields.END_CURSOR, null);
      return this;
    }

    @Override
    public GeneratedSearchQueryPageResponseStrictContract build() {
      return new GeneratedSearchQueryPageResponseStrictContract(
          this.totalItems, this.hasMoreTotalItems, this.startCursor, this.endCursor);
    }
  }

  public interface TotalItemsStep {
    HasMoreTotalItemsStep totalItems(final Long totalItems);
  }

  public interface HasMoreTotalItemsStep {
    OptionalStep hasMoreTotalItems(final Boolean hasMoreTotalItems);
  }

  public interface OptionalStep {
    OptionalStep startCursor(final @Nullable String startCursor);

    OptionalStep startCursor(
        final @Nullable String startCursor, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep endCursor(final @Nullable String endCursor);

    OptionalStep endCursor(
        final @Nullable String endCursor, final ContractPolicy.FieldPolicy<String> policy);

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
