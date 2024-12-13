/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.sorting;

import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import java.util.Optional;

public class SortRequestDto {

  public static final String SORT_BY = "sortBy";
  public static final String SORT_ORDER = "sortOrder";

  private String sortBy;

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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "SortRequestDto(sortBy=" + getSortBy() + ", sortOrder=" + getSortOrder() + ")";
  }
}
