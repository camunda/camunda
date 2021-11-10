/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.management.dto;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;

public class UsageMetricQueryDTO {
  private static final int DEFAULT_PAGE_SIZE = Integer.MAX_VALUE;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Date startTime;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Date endTime;

  private int pageSize = DEFAULT_PAGE_SIZE;
  private String[] searchAfter;
  private String[] searchBefore;

  public Date getStartTime() {
    return startTime;
  }

  public void setStartTime(Date startTime) {
    this.startTime = startTime;
  }

  public Date getEndTime() {
    return endTime;
  }

  public void setEndTime(Date endTime) {
    this.endTime = endTime;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }

  public String[] getSearchAfter() {
    return searchAfter;
  }

  public void setSearchAfter(String[] searchAfter) {
    this.searchAfter = searchAfter;
  }

  public String[] getSearchBefore() {
    return searchBefore;
  }

  public void setSearchBefore(String[] searchBefore) {
    this.searchBefore = searchBefore;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UsageMetricQueryDTO)) {
      return false;
    }
    final UsageMetricQueryDTO that = (UsageMetricQueryDTO) o;
    return pageSize == that.pageSize
        && Objects.equals(startTime, that.startTime)
        && Objects.equals(endTime, that.endTime)
        && Arrays.equals(searchAfter, that.searchAfter)
        && Arrays.equals(searchBefore, that.searchBefore);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(startTime, endTime, pageSize);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchBefore);
    return result;
  }
}
