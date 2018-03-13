package org.camunda.optimize.service.exceptions;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

/**
 * @author Askar Akhmerov
 */
public class ReportEvaluationException extends OptimizeException {

  protected ReportDefinitionDto reportDefinition;

  public ReportEvaluationException(ReportDefinitionDto reportDefinition, OptimizeException e) {
    super(e.getMessage(), e);
    this.reportDefinition = reportDefinition;
  }

  public ReportDefinitionDto getReportDefinition() {
    return reportDefinition;
  }

}
