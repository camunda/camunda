/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/user-tasks.yaml#/components/schemas/UserTaskAuditLogSearchQueryRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskAuditLogSearchQueryRequestStrictContract(
    @Nullable Object page,
    java.util.@Nullable List<GeneratedAuditLogSearchQuerySortRequestStrictContract> sort,
    @Nullable GeneratedUserTaskAuditLogFilterStrictContract filter
) {

  public static java.util.List<GeneratedAuditLogSearchQuerySortRequestStrictContract> coerceSort(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "sort must be a List of GeneratedAuditLogSearchQuerySortRequestStrictContract, but was " + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedAuditLogSearchQuerySortRequestStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedAuditLogSearchQuerySortRequestStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "sort must contain only GeneratedAuditLogSearchQuerySortRequestStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }


  public static GeneratedUserTaskAuditLogFilterStrictContract coerceFilter(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedUserTaskAuditLogFilterStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedUserTaskAuditLogFilterStrictContract, but was " + value.getClass().getName());
  }



  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object page;
    private Object sort;
    private Object filter;

    private Builder() {}

    @Override
    public OptionalStep page(final @Nullable Object page) {
      this.page = page;
      return this;
    }

    @Override
    public OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy) {
      this.page = policy.apply(page, Fields.PAGE, null);
      return this;
    }


    @Override
    public OptionalStep sort(final java.util.@Nullable List<GeneratedAuditLogSearchQuerySortRequestStrictContract> sort) {
      this.sort = sort;
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(final java.util.@Nullable List<GeneratedAuditLogSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedAuditLogSearchQuerySortRequestStrictContract>> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }

    @Override
    public OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sort = policy.apply(sort, Fields.SORT, null);
      return this;
    }


    @Override
    public OptionalStep filter(final @Nullable GeneratedUserTaskAuditLogFilterStrictContract filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter) {
      this.filter = filter;
      return this;
    }

    public Builder filter(final @Nullable GeneratedUserTaskAuditLogFilterStrictContract filter, final ContractPolicy.FieldPolicy<GeneratedUserTaskAuditLogFilterStrictContract> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public OptionalStep filter(final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy) {
      this.filter = policy.apply(filter, Fields.FILTER, null);
      return this;
    }

    @Override
    public GeneratedUserTaskAuditLogSearchQueryRequestStrictContract build() {
      return new GeneratedUserTaskAuditLogSearchQueryRequestStrictContract(
          this.page,
          coerceSort(this.sort),
          coerceFilter(this.filter));
    }
  }

  public interface OptionalStep {
  OptionalStep page(final @Nullable Object page);

  OptionalStep page(final @Nullable Object page, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep sort(final java.util.@Nullable List<GeneratedAuditLogSearchQuerySortRequestStrictContract> sort);

  OptionalStep sort(final @Nullable Object sort);

  OptionalStep sort(final java.util.@Nullable List<GeneratedAuditLogSearchQuerySortRequestStrictContract> sort, final ContractPolicy.FieldPolicy<java.util.List<GeneratedAuditLogSearchQuerySortRequestStrictContract>> policy);

  OptionalStep sort(final @Nullable Object sort, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep filter(final @Nullable GeneratedUserTaskAuditLogFilterStrictContract filter);

  OptionalStep filter(final @Nullable Object filter);

  OptionalStep filter(final @Nullable GeneratedUserTaskAuditLogFilterStrictContract filter, final ContractPolicy.FieldPolicy<GeneratedUserTaskAuditLogFilterStrictContract> policy);

  OptionalStep filter(final @Nullable Object filter, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedUserTaskAuditLogSearchQueryRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PAGE = ContractPolicy.field("UserTaskAuditLogSearchQueryRequest", "page");
    public static final ContractPolicy.FieldRef SORT = ContractPolicy.field("UserTaskAuditLogSearchQueryRequest", "sort");
    public static final ContractPolicy.FieldRef FILTER = ContractPolicy.field("UserTaskAuditLogSearchQueryRequest", "filter");

    private Fields() {}
  }


}
