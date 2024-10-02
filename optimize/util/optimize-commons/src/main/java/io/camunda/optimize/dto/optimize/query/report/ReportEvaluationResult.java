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
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "ReportEvaluationResult(reportDefinition=" + getReportDefinition() + ")";
  }
}