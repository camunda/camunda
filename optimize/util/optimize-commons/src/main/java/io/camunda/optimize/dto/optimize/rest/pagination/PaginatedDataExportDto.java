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
    final int PRIME = 59;
    int result = 1;
    final Object $searchRequestId = getSearchRequestId();
    result = result * PRIME + ($searchRequestId == null ? 43 : $searchRequestId.hashCode());
    final Object $message = getMessage();
    result = result * PRIME + ($message == null ? 43 : $message.hashCode());
    final Object $numberOfRecordsInResponse = getNumberOfRecordsInResponse();
    result =
        result * PRIME
            + ($numberOfRecordsInResponse == null ? 43 : $numberOfRecordsInResponse.hashCode());
    final long $totalNumberOfRecords = getTotalNumberOfRecords();
    result = result * PRIME + (int) ($totalNumberOfRecords >>> 32 ^ $totalNumberOfRecords);
    final Object $reportId = getReportId();
    result = result * PRIME + ($reportId == null ? 43 : $reportId.hashCode());
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PaginatedDataExportDto)) {
      return false;
    }
    final PaginatedDataExportDto other = (PaginatedDataExportDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$searchRequestId = getSearchRequestId();
    final Object other$searchRequestId = other.getSearchRequestId();
    if (this$searchRequestId == null
        ? other$searchRequestId != null
        : !this$searchRequestId.equals(other$searchRequestId)) {
      return false;
    }
    final Object this$message = getMessage();
    final Object other$message = other.getMessage();
    if (this$message == null ? other$message != null : !this$message.equals(other$message)) {
      return false;
    }
    final Object this$numberOfRecordsInResponse = getNumberOfRecordsInResponse();
    final Object other$numberOfRecordsInResponse = other.getNumberOfRecordsInResponse();
    if (this$numberOfRecordsInResponse == null
        ? other$numberOfRecordsInResponse != null
        : !this$numberOfRecordsInResponse.equals(other$numberOfRecordsInResponse)) {
      return false;
    }
    if (getTotalNumberOfRecords() != other.getTotalNumberOfRecords()) {
      return false;
    }
    final Object this$reportId = getReportId();
    final Object other$reportId = other.getReportId();
    if (this$reportId == null ? other$reportId != null : !this$reportId.equals(other$reportId)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    return true;
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
