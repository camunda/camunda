/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import lombok.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class ReportEvaluationResult<Result extends ReportResultDto,
  ReportDefinition extends ReportDefinitionDto> {

  protected final Result reportResult;
  protected final ReportDefinition reportDefinition;

  public ReportEvaluationResult(@NonNull Result reportResult, @NonNull ReportDefinition reportDefinition) {
    Objects.requireNonNull(reportResult, "The report result dto is not allowed to be null!");
    Objects.requireNonNull(reportDefinition, "The report data dto is not allowed to be null!");
    this.reportResult = reportResult;
    this.reportDefinition = reportDefinition;
  }

  public String getId() {
    return reportDefinition.getId();
  }

  public Result getResultAsDto() {
    return reportResult;
  }

  public abstract List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns);

  public ReportDefinition getReportDefinition() {
    return reportDefinition;
  }
}
