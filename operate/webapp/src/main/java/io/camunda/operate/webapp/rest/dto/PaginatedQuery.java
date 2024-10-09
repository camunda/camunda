/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PaginatedQuery<T extends PaginatedQuery<T>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PaginatedQuery.class);

  private static final int DEFAULT_PAGE_SIZE = 50;

  /** Page size. */
  protected Integer pageSize = DEFAULT_PAGE_SIZE;

  private SortingDto sorting;

  /** Search for process instances that goes exactly after the given sort values. */
  private SortValuesWrapper[] searchAfter;

  private SortValuesWrapper[] searchAfterOrEqual;

  /** Search for process instance that goes exactly before the given sort values. */
  private SortValuesWrapper[] searchBefore;

  private SortValuesWrapper[] searchBeforeOrEqual;

  public SortingDto getSorting() {
    return sorting;
  }

  public T setSorting(final SortingDto sorting) {
    if (sorting != null && !getValidSortByValues().contains(sorting.getSortBy())) {
      throw new InvalidRequestException(
          "SortBy parameter has invalid value: " + sorting.getSortBy());
    }
    this.sorting = sorting;
    return (T) this;
  }

  @JsonIgnore
  protected Set<String> getValidSortByValues() {
    return new HashSet<>();
  }

  @Schema(
      description =
          "Array of values (can be one): copy/paste of sortValues field from one of the objects.",
      example = "[1605160098477, 4629710542312628000]")
  public SortValuesWrapper[] getSearchAfter() {
    return searchAfter;
  }

  public T setSearchAfter(final SortValuesWrapper[] searchAfter) {
    this.searchAfter = searchAfter;
    return (T) this;
  }

  public Object[] getSearchAfter(final ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchAfter, objectMapper);
  }

  public SortValuesWrapper[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public T setSearchAfterOrEqual(final SortValuesWrapper[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return (T) this;
  }

  public Object[] getSearchAfterOrEqual(final ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchAfterOrEqual, objectMapper);
  }

  @Schema(
      description =
          "Array of values (can be one): copy/paste of sortValues field from one of the objects.",
      example = "[1605160098477, 4629710542312628000]")
  public SortValuesWrapper[] getSearchBefore() {
    return searchBefore;
  }

  public T setSearchBefore(final SortValuesWrapper[] searchBefore) {
    this.searchBefore = searchBefore;
    return (T) this;
  }

  public Object[] getSearchBefore(final ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchBefore, objectMapper);
  }

  public SortValuesWrapper[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public T setSearchBeforeOrEqual(final SortValuesWrapper[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
    return (T) this;
  }

  public Object[] getSearchBeforeOrEqual(final ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchBeforeOrEqual, objectMapper);
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public T setPageSize(final Integer pageSize) {
    this.pageSize = pageSize;
    return (T) this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(sorting, pageSize);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchAfterOrEqual);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchBeforeOrEqual);
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
    final PaginatedQuery that = (PaginatedQuery) o;
    return Objects.equals(sorting, that.sorting)
        && Arrays.equals(searchAfter, that.searchAfter)
        && Arrays.equals(searchAfterOrEqual, that.searchAfterOrEqual)
        && Arrays.equals(searchBefore, that.searchBefore)
        && Arrays.equals(searchBeforeOrEqual, that.searchBeforeOrEqual)
        && Objects.equals(pageSize, that.pageSize);
  }
}
