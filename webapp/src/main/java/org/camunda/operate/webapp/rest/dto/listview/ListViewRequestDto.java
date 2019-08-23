/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.listview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.camunda.operate.webapp.rest.dto.SortingDto;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.camunda.operate.util.CollectionUtil;

public class ListViewRequestDto {

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BY_START_DATE = "startDate";
  public static final String SORT_BY_END_DATE = "endDate";
  public static final String SORT_BY_WORKFLOW_NAME = "workflowName";
  public static final String SORT_BY_WORFLOW_VERSION = "workflowVersion";

  public static final List<String> VALID_SORT_BY_VALUES;
  static {
    VALID_SORT_BY_VALUES = new ArrayList<>();
    VALID_SORT_BY_VALUES.add(SORT_BY_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_START_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_END_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORKFLOW_NAME);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORFLOW_VERSION);
  }

  public ListViewRequestDto() {
  }

  public ListViewRequestDto(ListViewQueryDto query) {
    this(Arrays.asList(query));
  }

  public ListViewRequestDto(List<ListViewQueryDto> queries) {
    this();
    this.queries = queries;
  }

  private List<ListViewQueryDto> queries = new ArrayList<>();

  private SortingDto sorting;

  public List<ListViewQueryDto> getQueries() {
    return queries;
  }
  
  public boolean hasQueries() {
    return CollectionUtil.isNotEmpty(queries);
  }
  
  public boolean hasExactOneQuery() {
    return CollectionUtil.isNotEmpty(queries) && queries.size() == 1;
  }
  
  public ListViewQueryDto queryAt(int index) {
    return queries.get(index);
  }

  public void setQueries(List<ListViewQueryDto> queries) {
    this.queries = queries;
  }
  
  public ListViewRequestDto addQuery(ListViewQueryDto aQuery) {
    this.queries.add(aQuery);
    return this;
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
  
  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewRequestDto that = (ListViewRequestDto) o;

    if (queries != null ? !queries.equals(that.queries) : that.queries != null)
      return false;
    return sorting != null ? sorting.equals(that.sorting) : that.sorting == null;
  }

  @Override
  public int hashCode() {
    int result = queries != null ? queries.hashCode() : 0;
    result = 31 * result + (sorting != null ? sorting.hashCode() : 0);
    return result;
  }

}
