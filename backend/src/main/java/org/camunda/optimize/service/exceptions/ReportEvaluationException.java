package org.camunda.optimize.service.exceptions;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

/**
 * @author Askar Akhmerov
 */
public class ReportEvaluationException extends OptimizeException {

  protected ReportDefinitionDto reportDefinition;
  protected ReportDataDto reportDataDto;

  public ReportEvaluationException(ReportDefinitionDto reportDefinition, OptimizeException e) {
    super(e.getMessage(), e);
    this.reportDefinition = reportDefinition;
  }

  public ReportEvaluationException(ReportDataDto reportData, OptimizeException e) {
    super(e.getMessage(), e);
    this.reportDataDto = reportData;
  }

  public ReportDefinitionDto getReportDefinition() {
    return reportDefinition;
  }

  public ReportDataDto getReportDataDto() {
    return reportDataDto;
  }
}
