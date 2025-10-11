/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import java.util.List;
import java.util.Objects;

public class Page<T> {

  private Integer offset;
  private Integer limit;
  private Long total;
  private String sortBy;
  private SortOrder sortOrder;
  private List<T> results;

  public Page(
      final Integer offset,
      final Integer limit,
      final Long total,
      final String sortBy,
      final SortOrder sortOrder,
      final List<T> results) {
    this.offset = offset;
    this.limit = limit;
    this.total = total;
    this.sortBy = sortBy;
    this.sortOrder = sortOrder;
    this.results = results;
  }

  public Page() {}

  public Integer getOffset() {
    return offset;
  }

  public void setOffset(final Integer offset) {
    this.offset = offset;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(final Integer limit) {
    this.limit = limit;
  }

  public Long getTotal() {
    return total;
  }

  public void setTotal(final Long total) {
    this.total = total;
  }

  public String getSortBy() {
    return sortBy;
  }

  public void setSortBy(final String sortBy) {
    this.sortBy = sortBy;
  }

  public SortOrder getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(final SortOrder sortOrder) {
    this.sortOrder = sortOrder;
  }

  public List<T> getResults() {
    return results;
  }

  public void setResults(final List<T> results) {
    this.results = results;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof Page;
  }

  @Override
  public int hashCode() {
    return Objects.hash(offset, limit, total, sortBy, sortOrder, results);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Page<?> page = (Page<?>) o;
    return Objects.equals(offset, page.offset)
        && Objects.equals(limit, page.limit)
        && Objects.equals(total, page.total)
        && Objects.equals(sortBy, page.sortBy)
        && Objects.equals(sortOrder, page.sortOrder)
        && Objects.equals(results, page.results);
  }

  @Override
  public String toString() {
    return "Page(offset="
        + getOffset()
        + ", limit="
        + getLimit()
        + ", total="
        + getTotal()
        + ", sortBy="
        + getSortBy()
        + ", sortOrder="
        + getSortOrder()
        + ", results="
        + getResults()
        + ")";
  }
}
