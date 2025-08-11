/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.management.dto;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;

public class UsageMetricQueryDTO {
  private static final int DEFAULT_PAGE_SIZE = Integer.MAX_VALUE;

  @DateTimeFormat(pattern = OperateDateTimeFormatter.DATE_FORMAT_DEFAULT)
  private OffsetDateTime startTime;

  @DateTimeFormat(pattern = OperateDateTimeFormatter.DATE_FORMAT_DEFAULT)
  private OffsetDateTime endTime;

  private String tenantId;

  private int pageSize = DEFAULT_PAGE_SIZE;

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(final int pageSize) {
    this.pageSize = pageSize;
  }

  @Override
  public int hashCode() {
    return Objects.hash(startTime, endTime, pageSize);
  }

  @Override
  public boolean equals(final Object o) {
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
}
