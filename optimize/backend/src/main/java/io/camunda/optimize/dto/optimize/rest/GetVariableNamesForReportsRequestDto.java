/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.List;

public class GetVariableNamesForReportsRequestDto {

  private List<String> reportIds;

  public GetVariableNamesForReportsRequestDto() {}

  public List<String> getReportIds() {
    return reportIds;
  }

  public void setReportIds(final List<String> reportIds) {
    this.reportIds = reportIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof GetVariableNamesForReportsRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reportIds = getReportIds();
    result = result * PRIME + ($reportIds == null ? 43 : $reportIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof GetVariableNamesForReportsRequestDto)) {
      return false;
    }
    final GetVariableNamesForReportsRequestDto other = (GetVariableNamesForReportsRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$reportIds = getReportIds();
    final Object other$reportIds = other.getReportIds();
    if (this$reportIds == null
        ? other$reportIds != null
        : !this$reportIds.equals(other$reportIds)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "GetVariableNamesForReportsRequestDto(reportIds=" + getReportIds() + ")";
  }
}
