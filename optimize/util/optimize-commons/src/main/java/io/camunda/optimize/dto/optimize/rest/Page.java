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
    final int PRIME = 59;
    int result = 1;
    final Object $offset = getOffset();
    result = result * PRIME + ($offset == null ? 43 : $offset.hashCode());
    final Object $limit = getLimit();
    result = result * PRIME + ($limit == null ? 43 : $limit.hashCode());
    final Object $total = getTotal();
    result = result * PRIME + ($total == null ? 43 : $total.hashCode());
    final Object $sortBy = getSortBy();
    result = result * PRIME + ($sortBy == null ? 43 : $sortBy.hashCode());
    final Object $sortOrder = getSortOrder();
    result = result * PRIME + ($sortOrder == null ? 43 : $sortOrder.hashCode());
    final Object $results = getResults();
    result = result * PRIME + ($results == null ? 43 : $results.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Page)) {
      return false;
    }
    final Page<?> other = (Page<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$offset = getOffset();
    final Object other$offset = other.getOffset();
    if (this$offset == null ? other$offset != null : !this$offset.equals(other$offset)) {
      return false;
    }
    final Object this$limit = getLimit();
    final Object other$limit = other.getLimit();
    if (this$limit == null ? other$limit != null : !this$limit.equals(other$limit)) {
      return false;
    }
    final Object this$total = getTotal();
    final Object other$total = other.getTotal();
    if (this$total == null ? other$total != null : !this$total.equals(other$total)) {
      return false;
    }
    final Object this$sortBy = getSortBy();
    final Object other$sortBy = other.getSortBy();
    if (this$sortBy == null ? other$sortBy != null : !this$sortBy.equals(other$sortBy)) {
      return false;
    }
    final Object this$sortOrder = getSortOrder();
    final Object other$sortOrder = other.getSortOrder();
    if (this$sortOrder == null
        ? other$sortOrder != null
        : !this$sortOrder.equals(other$sortOrder)) {
      return false;
    }
    final Object this$results = getResults();
    final Object other$results = other.getResults();
    if (this$results == null ? other$results != null : !this$results.equals(other$results)) {
      return false;
    }
    return true;
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
