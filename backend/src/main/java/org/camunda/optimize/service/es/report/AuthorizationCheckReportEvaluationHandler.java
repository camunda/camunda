package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;

@Component
public class AuthorizationCheckReportEvaluationHandler extends ReportEvaluationHandler {

  public ReportResultDto evaluateSavedReport(String userId, String reportId) {
    return super.evaluateSavedReport(userId, reportId);
  }

  public ReportResultDto evaluateReport(String userId, ReportDefinitionDto reportDefinition) {
    return super.evaluateReport(userId, reportDefinition);
  }

  @Autowired
  private SessionService sessionService;

  protected boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto report) {
    if (SINGLE_REPORT_TYPE.equals(report.getReportType())) {
      SingleReportDefinitionDto singleReport = (SingleReportDefinitionDto) report;
      SingleReportDataDto reportData = singleReport.getData();
      return reportData == null ||
        sessionService.isAuthorizedToSeeDefinition(userId, reportData.getProcessDefinitionKey());
    }
    return true;
  }
}
