/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;

public class EvaluationResultDto<Result extends ReportResultDto, ReportDefinition extends ReportDefinitionDto> {

  protected Result result;
  @JsonUnwrapped
  protected ReportDefinition reportDefinition;

  public static <T extends ReportResultDto, R extends ReportDefinitionDto> EvaluationResultDto<T, R> from(
    final ReportEvaluationResult<T, R> reportEvaluationResult) {

    return new EvaluationResultDto<>(
      reportEvaluationResult.getResultAsDto(),
      reportEvaluationResult.getReportDefinition()
    );
  }

  protected EvaluationResultDto() {
  }

  public EvaluationResultDto(final Result result, final ReportDefinition reportDefinition) {
    this.result = result;
    this.reportDefinition = reportDefinition;
  }

  public Result getResult() {
    return result;
  }

  public void setResult(final Result result) {
    this.result = result;
  }

  public ReportDefinition getReportDefinition() {
    return reportDefinition;
  }

  public void setReportDefinition(final ReportDefinition reportDefinition) {
    this.reportDefinition = reportDefinition;
  }
}
