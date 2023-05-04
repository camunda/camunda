/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;

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

  public SortingDto setSortBy(String sortBy) {
    this.sortBy = sortBy;
    return this;
  }

  @Schema(description = "Sort order, default: asc", allowableValues = "asc,desc", required = false)
  public String getSortOrder() {
    return sortOrder;
  }

  public SortingDto setSortOrder(String sortOrder) {
    if (!VALID_SORT_ORDER_VALUES.contains(sortOrder)) {
      throw new InvalidRequestException("SortOrder parameter has invalid value: " + sortOrder);
    }
    this.sortOrder = sortOrder;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    SortingDto that = (SortingDto) o;

    if (sortBy != null ? !sortBy.equals(that.sortBy) : that.sortBy != null)
      return false;
    return sortOrder != null ? sortOrder.equals(that.sortOrder) : that.sortOrder == null;
  }

  @Override
  public int hashCode() {
    int result = sortBy != null ? sortBy.hashCode() : 0;
    result = 31 * result + (sortOrder != null ? sortOrder.hashCode() : 0);
    return result;
  }
}
