/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/search-models.yaml#/components/schemas/LimitPagination
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedLimitPaginationStrictContract(
    @Nullable Integer limit
) {


  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Integer limit;

    private Builder() {}

    @Override
    public OptionalStep limit(final @Nullable Integer limit) {
      this.limit = limit;
      return this;
    }

    @Override
    public OptionalStep limit(final @Nullable Integer limit, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.limit = policy.apply(limit, Fields.LIMIT, null);
      return this;
    }

    @Override
    public GeneratedLimitPaginationStrictContract build() {
      return new GeneratedLimitPaginationStrictContract(
          this.limit);
    }
  }

  public interface OptionalStep {
  OptionalStep limit(final @Nullable Integer limit);

  OptionalStep limit(final @Nullable Integer limit, final ContractPolicy.FieldPolicy<Integer> policy);


    GeneratedLimitPaginationStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef LIMIT = ContractPolicy.field("LimitPagination", "limit");

    private Fields() {}
  }


}
