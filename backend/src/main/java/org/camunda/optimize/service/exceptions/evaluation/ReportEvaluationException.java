package org.camunda.optimize.service.exceptions.evaluation;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

public class ReportEvaluationException extends OptimizeRuntimeException {

  protected ReportDefinitionDto reportDefinition;

  public ReportEvaluationException() {
    super();
  }

  public ReportEvaluationException(String message) {
    super(message);
  }

  public ReportEvaluationException(String message, Exception e) {
    super(message, e);
  }

  public ReportEvaluationException(ReportDefinitionDto reportDefinition, Exception e) {
    super(e.getMessage(), e);
    setReportDefinition(reportDefinition);
  }

  public void setReportDefinition(ReportDefinitionDto reportDefinition) {
    this.reportDefinition = reportDefinition;
  }

  public ReportDefinitionDto getReportDefinition() {
    return reportDefinition;
  }
}
