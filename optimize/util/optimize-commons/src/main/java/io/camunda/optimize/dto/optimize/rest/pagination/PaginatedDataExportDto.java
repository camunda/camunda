/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collection;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedDataExportDto {

  private String searchRequestId;
  private String message;
  private Integer numberOfRecordsInResponse;
  private long totalNumberOfRecords;
  private String reportId;
  private Object data;

  public PaginatedDataExportDto(
      final String searchRequestId,
      final String message,
      final Integer numberOfRecordsInResponse,
      final long totalNumberOfRecords,
      final String reportId,
      final Object data) {
    this.searchRequestId = searchRequestId;
    this.message = message;
    this.numberOfRecordsInResponse = numberOfRecordsInResponse;
    this.totalNumberOfRecords = totalNumberOfRecords;
    this.reportId = reportId;
    this.data = data;
  }

  public PaginatedDataExportDto() {}

  public String getSearchRequestId() {
    return searchRequestId;
  }

  public void setSearchRequestId(final String searchRequestId) {
    this.searchRequestId = searchRequestId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public Integer getNumberOfRecordsInResponse() {
    return numberOfRecordsInResponse;
  }

  public void setNumberOfRecordsInResponse(final Integer numberOfRecordsInResponse) {
    this.numberOfRecordsInResponse = numberOfRecordsInResponse;
  }

  public long getTotalNumberOfRecords() {
    return totalNumberOfRecords;
  }

  public void setTotalNumberOfRecords(final long totalNumberOfRecords) {
    this.totalNumberOfRecords = totalNumberOfRecords;
  }

  public String getReportId() {
    return reportId;
  }

  public void setReportId(final String reportId) {
    this.reportId = reportId;
  }

  public Object getData() {
    return data;
  }

  public void setData(final Object data) {
    this.data = data;
    if (data == null) {
      numberOfRecordsInResponse = 0;
    } else if (data instanceof Collection) {
      numberOfRecordsInResponse = ((Collection<?>) data).size();
    } else {
      numberOfRecordsInResponse = 1;
    }
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PaginatedDataExportDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "PaginatedDataExportDto(searchRequestId="
        + getSearchRequestId()
        + ", message="
        + getMessage()
        + ", numberOfRecordsInResponse="
        + getNumberOfRecordsInResponse()
        + ", totalNumberOfRecords="
        + getTotalNumberOfRecords()
        + ", reportId="
        + getReportId()
        + ", data="
        + getData()
        + ")";
  }
}
