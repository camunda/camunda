/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Sorting")
public class SortingDto {

  public static final String SORT_ORDER_ASC_VALUE = "asc";
  public static final String SORT_ORDER_DESC_VALUE = "desc";

  public static final List<String> VALID_SORT_ORDER_VALUES;

  static {
    VALID_SORT_ORDER_VALUES = new ArrayList<>();
    VALID_SORT_ORDER_VALUES.add(SORT_ORDER_ASC_VALUE);
    VALID_SORT_ORDER_VALUES.add(SORT_ORDER_DESC_VALUE);
  }

  private String sortBy;
  private String sortOrder = SORT_ORDER_ASC_VALUE;

  @Schema(description = "Data field to sort by", required = true)
  public String getSortBy() {
    return sortBy;
  }

  public SortingDto setSortBy(final String sortBy) {
    this.sortBy = sortBy;
    return this;
  }

  @Schema(description = "Sort order, default: asc", allowableValues = "asc,desc", required = false)
  public String getSortOrder() {
    return sortOrder;
  }

  public SortingDto setSortOrder(final String sortOrder) {
    if (!VALID_SORT_ORDER_VALUES.contains(sortOrder)) {
      throw new InvalidRequestException("SortOrder parameter has invalid value: " + sortOrder);
    }
    this.sortOrder = sortOrder;
    return this;
  }

  @Override
  public int hashCode() {
    int result = sortBy != null ? sortBy.hashCode() : 0;
    result = 31 * result + (sortOrder != null ? sortOrder.hashCode() : 0);
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

    final SortingDto that = (SortingDto) o;

    if (sortBy != null ? !sortBy.equals(that.sortBy) : that.sortBy != null) {
      return false;
    }
    return sortOrder != null ? sortOrder.equals(that.sortOrder) : that.sortOrder == null;
  }
}
