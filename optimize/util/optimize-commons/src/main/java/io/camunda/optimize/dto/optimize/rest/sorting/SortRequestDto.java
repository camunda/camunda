/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.sorting;

import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import jakarta.ws.rs.QueryParam;
import java.util.Optional;

public class SortRequestDto {

  public static final String SORT_BY = "sortBy";
  public static final String SORT_ORDER = "sortOrder";

  @QueryParam(SORT_BY)
  private String sortBy;

  @QueryParam(SORT_ORDER)
  private SortOrder sortOrder;

  public SortRequestDto(final String sortBy, final SortOrder sortOrder) {
    this.sortBy = sortBy;
    this.sortOrder = sortOrder;
  }

  public SortRequestDto() {}

  public Optional<String> getSortBy() {
    return Optional.ofNullable(sortBy);
  }

  public void setSortBy(final String sortBy) {
    this.sortBy = sortBy;
  }

  public Optional<SortOrder> getSortOrder() {
    return Optional.ofNullable(sortOrder);
  }

  public void setSortOrder(final SortOrder sortOrder) {
    this.sortOrder = sortOrder;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof SortRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $sortBy = getSortBy();
    result = result * PRIME + ($sortBy == null ? 43 : $sortBy.hashCode());
    final Object $sortOrder = getSortOrder();
    result = result * PRIME + ($sortOrder == null ? 43 : $sortOrder.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SortRequestDto)) {
      return false;
    }
    final SortRequestDto other = (SortRequestDto) o;
    if (!other.canEqual((Object) this)) {
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
    return true;
  }

  @Override
  public String toString() {
    return "SortRequestDto(sortBy=" + getSortBy() + ", sortOrder=" + getSortOrder() + ")";
  }
}
