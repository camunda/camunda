/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;

/** The request to get the list of batch operations, created by current user. */
public class BatchOperationRequestDto {

  /** Search for the batch operations that goes exactly before the given sort values. */
  private SortValuesWrapper[] searchBefore;

  /** Search for the batch operations that goes exactly after the given sort values. */
  private SortValuesWrapper[] searchAfter;

  /** Page size. */
  private Integer pageSize;

  public BatchOperationRequestDto() {}

  public BatchOperationRequestDto(
      final Integer pageSize,
      final SortValuesWrapper[] searchAfter,
      final SortValuesWrapper[] searchBefore) {
    this.pageSize = pageSize;
    this.searchAfter = searchAfter;
    this.searchBefore = searchBefore;
  }

  @Schema(
      description =
          "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"9223372036854775807\", \"1583836503404\"]")
  public SortValuesWrapper[] getSearchBefore() {
    return searchBefore;
  }

  public BatchOperationRequestDto setSearchBefore(final SortValuesWrapper[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  public Object[] getSearchBefore(final ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchBefore, objectMapper);
  }

  @Schema(
      description =
          "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"1583836151645\", \"1583836128180\"]")
  public SortValuesWrapper[] getSearchAfter() {
    return searchAfter;
  }

  public BatchOperationRequestDto setSearchAfter(final SortValuesWrapper[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public Object[] getSearchAfter(final ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchAfter, objectMapper);
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public BatchOperationRequestDto setPageSize(final Integer pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + (pageSize != null ? pageSize.hashCode() : 0);
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

    final BatchOperationRequestDto that = (BatchOperationRequestDto) o;

    // Probably incorrect - comparing Object[] arrays with Arrays.equals
    if (!Arrays.equals(searchBefore, that.searchBefore)) {
      return false;
    }
    // Probably incorrect - comparing Object[] arrays with Arrays.equals
    if (!Arrays.equals(searchAfter, that.searchAfter)) {
      return false;
    }
    return pageSize != null ? pageSize.equals(that.pageSize) : that.pageSize == null;
  }
}
