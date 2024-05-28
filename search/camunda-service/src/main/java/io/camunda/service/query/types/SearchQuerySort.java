/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.types;

import static io.camunda.data.clients.types.DataStoreSortOptionsBuilders.reverseOrder;
import static io.camunda.data.clients.types.DataStoreSortOptionsBuilders.sortOptions;
import static io.camunda.data.clients.types.DataStoreSortOptionsBuilders.toSortOrder;

import io.camunda.data.clients.types.DataStoreSortOptions;
import io.camunda.data.clients.types.SortOrder;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final class SearchQuerySort {

  private final String field;
  private final SortOrder order;

  private SearchQuerySort(final Builder builder) {
    field = builder.field;
    order = builder.order;
  }

  public String field() {
    return field;
  }

  public String order() {
    return order();
  }

  public boolean asc() {
    return order != null && order == SortOrder.ASC;
  }

  public boolean desc() {
    return order != null && order == SortOrder.DESC;
  }

  public static SearchQuerySort of(
      final Function<Builder, DataStoreObjectBuilder<SearchQuerySort>> fn) {
    return fn.apply(new Builder()).build();
  }

  public DataStoreSortOptions toSort(final boolean reverseOrder) {
    if (field != null && order != null) {
      if (!reverseOrder) {
        return sortOptions(field, order, "_last");
      } else {
        return sortOptions(field, reverseOrder(order), "_first");
      }
    }
    return null;
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, order);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SearchQuerySort that = (SearchQuerySort) o;
    return Objects.equals(field, that.field) && order == that.order;
  }

  public static final class Builder implements DataStoreObjectBuilder<SearchQuerySort> {

    private String field;
    private SortOrder order;

    public Builder field(final String field) {
      this.field = field;
      return this;
    }

    public Builder asc() {
      order = SortOrder.ASC;
      return this;
    }

    public Builder desc() {
      order = SortOrder.DESC;
      return this;
    }

    public Builder order(final SortOrder order) {
      this.order = order;
      return this;
    }

    public Builder order(final String order) {
      this.order = toSortOrder(order);
      return this;
    }

    @Override
    public SearchQuerySort build() {
      return new SearchQuerySort(this);
    }
  }
}
