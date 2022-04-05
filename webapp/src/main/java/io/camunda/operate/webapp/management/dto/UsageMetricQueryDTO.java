/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management.dto;

import io.camunda.operate.property.ElasticsearchProperties;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class UsageMetricQueryDTO {
  private static final int DEFAULT_PAGE_SIZE = Integer.MAX_VALUE;

  @DateTimeFormat(pattern = ElasticsearchProperties.DATE_FORMAT_DEFAULT)
  private OffsetDateTime startTime;

  @DateTimeFormat(pattern = ElasticsearchProperties.DATE_FORMAT_DEFAULT)
  private OffsetDateTime endTime;

  private int pageSize = DEFAULT_PAGE_SIZE;

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
        && Objects.equals(endTime, that.endTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(startTime, endTime, pageSize);
  }
}
