/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.listview;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.camunda.operate.webapp.rest.dto.SortingDto;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;

@ApiModel("Process instances request")
public class ListViewRequestDto {

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BY_START_DATE = "startDate";
  public static final String SORT_BY_END_DATE = "endDate";
  public static final String SORT_BY_PROCESS_NAME = "processName";
  public static final String SORT_BY_WORFLOW_VERSION = "processVersion";

  public static final List<String> VALID_SORT_BY_VALUES;
  static {
    VALID_SORT_BY_VALUES = new ArrayList<>();
    VALID_SORT_BY_VALUES.add(SORT_BY_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_START_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_END_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_PROCESS_NAME);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORFLOW_VERSION);
  }

  private ListViewQueryDto query;

  private SortingDto sorting;

  /**
   * Search for process instances that goes exactly after the given sort values.
   */
  private Object[] searchAfter;

  /**
   * Search for process instance that goes exactly before the given sort values.
   */
  private Object[] searchBefore;

  /**
   * Page size.
   */
  private Integer pageSize = 50;

  public ListViewRequestDto() {
  }

  public ListViewRequestDto(final ListViewQueryDto query) {
    this.query = query;
  }

  public ListViewQueryDto getQuery() {
    return query;
  }

  public void setQuery(final ListViewQueryDto query) {
    this.query = query;
  }

  public SortingDto getSorting() {
    return sorting;
  }

  public void setSorting(SortingDto sorting) {
    if (sorting != null && !VALID_SORT_BY_VALUES.contains(sorting.getSortBy())) {
      throw new InvalidRequestException("SortBy parameter has invalid value: " + sorting.getSortBy());
    }
    this.sorting = sorting;
  }

  @ApiModelProperty(value= "Array of two values: copy/paste of sortValues field from one of the process instances.",
      example = "[1605160098477, 4629710542312628000]")
  public Object[] getSearchAfter() {
    return searchAfter;
  }

  public void setSearchAfter(final Object[] searchAfter) {
    this.searchAfter = searchAfter;
  }

  @ApiModelProperty(value= "Array of two values: copy/paste of sortValues field from one of the process instances.",
      example = "[1605160098477, 4629710542312628000]")
  public Object[] getSearchBefore() {
    return searchBefore;
  }

  public void setSearchBefore(final Object[] searchBefore) {
    this.searchBefore = searchBefore;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public void setPageSize(final Integer pageSize) {
    this.pageSize = pageSize;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListViewRequestDto that = (ListViewRequestDto) o;
    return Objects.equals(query, that.query) &&
        Objects.equals(sorting, that.sorting) &&
        Arrays.equals(searchAfter, that.searchAfter) &&
        Arrays.equals(searchBefore, that.searchBefore) &&
        Objects.equals(pageSize, that.pageSize);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(query, sorting, pageSize);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchBefore);
    return result;
  }
}
