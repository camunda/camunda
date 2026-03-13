/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/element-instances.yaml#/components/schemas/ElementInstanceSearchQuerySortRequest
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
public record GeneratedElementInstanceSearchQuerySortRequestStrictContract(
    String field,
    io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order
) {

  public GeneratedElementInstanceSearchQuerySortRequestStrictContract {
    Objects.requireNonNull(field, "field is required and must not be null");
  }


  public static FieldStep builder() {
    return new Builder();
  }

  public static final class Builder implements FieldStep, OptionalStep {
    private String field;
    private io.camunda.gateway.protocol.model.SortOrderEnum order;

    private Builder() {}

    @Override
    public OptionalStep field(final String field) {
      this.field = field;
      return this;
    }

    @Override
    public OptionalStep order(final io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order) {
      this.order = order;
      return this;
    }

    @Override
    public OptionalStep order(final io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order, final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.SortOrderEnum> policy) {
      this.order = policy.apply(order, Fields.ORDER, null);
      return this;
    }

    @Override
    public GeneratedElementInstanceSearchQuerySortRequestStrictContract build() {
      return new GeneratedElementInstanceSearchQuerySortRequestStrictContract(
          this.field,
          this.order);
    }
  }

  public interface FieldStep {
    OptionalStep field(final String field);
  }

  public interface OptionalStep {
  OptionalStep order(final io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order);

  OptionalStep order(final io.camunda.gateway.protocol.model.@Nullable SortOrderEnum order, final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.SortOrderEnum> policy);


    GeneratedElementInstanceSearchQuerySortRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef FIELD = ContractPolicy.field("ElementInstanceSearchQuerySortRequest", "field");
    public static final ContractPolicy.FieldRef ORDER = ContractPolicy.field("ElementInstanceSearchQuerySortRequest", "order");

    private Fields() {}
  }


}
