/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public abstract class ReportEvaluationResult {

  protected ReportDefinitionDto<?> reportDefinition;

  public ReportEvaluationResult(final ReportDefinitionDto<?> reportDefinition) {
    if (reportDefinition == null) {
      throw new IllegalArgumentException("reportDefinition cannot be null");
    }

    this.reportDefinition = reportDefinition;
  }

  public ReportEvaluationResult() {}

  public String getId() {
    return reportDefinition.getId();
  }

  public abstract List<String[]> getResultAsCsv(
      final Integer limit, final Integer offset, final ZoneId timezone);

  public abstract PaginatedDataExportDto getResult();

  public ReportDefinitionDto<?> getReportDefinition() {
    return reportDefinition;
  }

  public void setReportDefinition(final ReportDefinitionDto<?> reportDefinition) {
    if (reportDefinition == null) {
      throw new IllegalArgumentException("reportDefinition cannot be null");
    }

    this.reportDefinition = reportDefinition;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportEvaluationResult;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ReportEvaluationResult that = (ReportEvaluationResult) o;
    return Objects.equals(reportDefinition, that.reportDefinition);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(reportDefinition);
  }

  @Override
  public String toString() {
    return "ReportEvaluationResult(reportDefinition=" + getReportDefinition() + ")";
  }
}
