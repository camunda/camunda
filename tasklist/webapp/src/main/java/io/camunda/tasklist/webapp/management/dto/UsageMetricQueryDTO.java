/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.management.dto;

import io.camunda.tasklist.property.ElasticsearchProperties;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;

public class UsageMetricQueryDTO {
  private static final int DEFAULT_PAGE_SIZE = Integer.MAX_VALUE;

  @DateTimeFormat(pattern = ElasticsearchProperties.DATE_FORMAT_DEFAULT)
  private OffsetDateTime startTime;

  @DateTimeFormat(pattern = ElasticsearchProperties.DATE_FORMAT_DEFAULT)
  private OffsetDateTime endTime;

  private String tenantId;

  private int pageSize = DEFAULT_PAGE_SIZE;
  private String[] searchAfter;
  private String[] searchBefore;

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(OffsetDateTime endTime) {
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

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
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
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(searchAfter, that.searchAfter)
        && Arrays.equals(searchBefore, that.searchBefore);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(startTime, endTime, tenantId, pageSize);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchBefore);
    return result;
  }
}
