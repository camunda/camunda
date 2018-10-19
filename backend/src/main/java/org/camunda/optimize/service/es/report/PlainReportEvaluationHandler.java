package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.springframework.stereotype.Component;

@Component
public class PlainReportEvaluationHandler extends ReportEvaluationHandler {

  public ReportResultDto evaluateSavedReport(String reportId) {
    return this.evaluateSavedReport(null, reportId);
  }

  public ReportResultDto evaluateReport(ReportDefinitionDto reportDefinition) {
    return this.evaluateReport(null, reportDefinition);
  }

  @Override
  protected boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto report) {
    return true;
  }
}
