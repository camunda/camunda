/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.List;
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GetVariableNamesForReportsRequestDto that = (GetVariableNamesForReportsRequestDto) o;
    return Objects.equals(reportIds, that.reportIds);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(reportIds);
  }

  @Override
  public String toString() {
    return "GetVariableNamesForReportsRequestDto(reportIds=" + getReportIds() + ")";
  }
}
