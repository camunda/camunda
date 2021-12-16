/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import java.util.Arrays;
import java.util.Objects;

public class Query<T> {

  public enum SortOrder {
    ASC, DESC
  }

  private T example = null;

  // standard paging paging method
  private int from = 0;
  private int size = 10;

  // search_after paging method
  private Object[] searchAfter = null;

  // Specify sorting, for now only by one field
  private String sortBy = null;
  private SortOrder sortOrder = SortOrder.ASC;

  public Query(){
    super();
  }

  public int getFrom() {
    return from;
  }

  public Query<T> setFrom(final int from) {
    this.from = from;
    return this;
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

  public Query<T> setSearchAfter(final  Object[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public String getSortBy() {
    return sortBy;
  }

  public Query<T> setSortBy(final String sortBy) {
    this.sortBy = sortBy;
    return this;
  }

  public SortOrder getSortOrder() {
    return sortOrder;
  }

  public Query<T> setSortOrder(final SortOrder sortOrder) {
    this.sortOrder = sortOrder;
    return this;
  }

  public T getExample() {
    return example;
  }

  public Query<T> setExample(final T example) {
    this.example = example;
    return this;
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
    return from == query.from && size == query.size && Objects.equals(example,
        query.example) && Arrays.equals(searchAfter, query.searchAfter)
        && Objects.equals(sortBy, query.sortBy) && sortOrder == query.sortOrder;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(example, from, size, sortBy, sortOrder);
    result = 31 * result + Arrays.hashCode(searchAfter);
    return result;
  }

  @Override
  public String toString() {
    return "Query{" +
        "example=" + example +
        ", from=" + from +
        ", size=" + size +
        ", searchAfter=" + Arrays.toString(searchAfter) +
        ", sortBy='" + sortBy + '\'' +
        ", sortOrder=" + sortOrder +
        '}';
  }
}
