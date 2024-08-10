/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Query<T> {

  private T filter;
  private int size = 10;
  // search_after paging method
  private Object[] searchAfter = null;
  private List<Sort> sort = null;

  public Query() {
    super();
  }

  public int getSize() {
    return size;
  }

  public Query<T> setSize(final int size) {
    this.size = size;
    return this;
  }

  public Object[] getSearchAfter() {
    return searchAfter;
  }

  public Query<T> setSearchAfter(final Object[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public List<Sort> getSort() {
    return sort;
  }

  public Query<T> setSort(final List<Sort> sort) {
    this.sort = sort;
    return this;
  }

  public T getFilter() {
    return filter;
  }

  public Query<T> setFilter(final T filter) {
    this.filter = filter;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(filter, size, sort);
    result = 31 * result + Arrays.hashCode(searchAfter);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Query<?> query = (Query<?>) o;
    return size == query.size
        && Objects.equals(filter, query.filter)
        && Arrays.equals(searchAfter, query.searchAfter)
        && Objects.equals(sort, query.sort);
  }

  @Override
  public String toString() {
    return "Query{"
        + "filter="
        + filter
        + ", size="
        + size
        + ", searchAfter="
        + Arrays.toString(searchAfter)
        + ", sort="
        + sort
        + '}';
  }

  public static class Sort {

    private String field;
    private Order order = Order.ASC;

    public static Sort of(String field, Order order) {
      return new Sort().setField(field).setOrder(order);
    }

    public static Sort of(String field) {
      return of(field, Order.ASC);
    }

    public static List<Sort> listOf(String field, Order order) {
      return List.of(of(field, order));
    }

    public static List<Sort> listOf(String field) {
      return List.of(of(field));
    }

    public String getField() {
      return field;
    }

    public Sort setField(final String field) {
      this.field = field;
      return this;
    }

    public Order getOrder() {
      return order;
    }

    public Sort setOrder(final Order order) {
      this.order = order;
      return this;
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
      final Sort sort = (Sort) o;
      return Objects.equals(field, sort.field) && order == sort.order;
    }

    public enum Order {
      ASC,
      DESC
    }
  }
}
