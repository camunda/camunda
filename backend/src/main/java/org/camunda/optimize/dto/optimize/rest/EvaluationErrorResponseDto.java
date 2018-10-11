package org.camunda.optimize.dto.optimize.rest;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

public class EvaluationErrorResponseDto extends ErrorResponseDto {
  private ReportDefinitionDto<?> reportDefinition;

  public EvaluationErrorResponseDto() {
  }

  public EvaluationErrorResponseDto(String errorMessage, ReportDefinitionDto<?> reportDefinition) {
    super(errorMessage);
    this.reportDefinition = reportDefinition;
  }

  public ReportDefinitionDto<?> getReportDefinition() {
    return reportDefinition;
  }
}
