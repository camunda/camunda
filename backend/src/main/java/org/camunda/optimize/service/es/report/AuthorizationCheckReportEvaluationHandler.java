package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


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
    if (report instanceof SingleReportDefinitionDto) {
      if (report.getData() instanceof ProcessReportDataDto) {
        ProcessReportDataDto reportData = (ProcessReportDataDto) report.getData();
        return sessionService.isAuthorizedToSeeProcessDefinition(userId, reportData.getProcessDefinitionKey());
      } else if (report.getData() instanceof DecisionReportDataDto) {
        DecisionReportDataDto reportData = (DecisionReportDataDto) report.getData();
        return sessionService.isAuthorizedToSeeDecisionDefinition(userId, reportData.getDecisionDefinitionKey());
      } else {
        return true;
      }
    }
    return true;
  }
}
