package org.camunda.optimize.service.exceptions;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

public class ReportEvaluationException extends OptimizeRuntimeException {

  protected ReportDefinitionDto reportDefinition;

  public ReportEvaluationException() {
  }

  public ReportEvaluationException(ReportDefinitionDto reportDefinition, Exception e) {
    super(e.getMessage(), e);
    this.reportDefinition = reportDefinition;
  }

  public ReportDefinitionDto getReportDefinition() {
    return reportDefinition;
  }

}
