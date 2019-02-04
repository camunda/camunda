package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class AuthorizationCheckReportEvaluationHandler extends ReportEvaluationHandler {

  public AuthorizationCheckReportEvaluationHandler(ReportReader reportReader,
                                                   SingleReportEvaluator singleReportEvaluator,
                                                   CombinedReportEvaluator combinedReportEvaluator) {
    super(reportReader, singleReportEvaluator, combinedReportEvaluator);
  }

  public ReportResult evaluateSavedReport(String userId, String reportId) {
    return super.evaluateSavedReport(userId, reportId);
  }

  public ReportResult evaluateReport(String userId, ReportDefinitionDto reportDefinition) {
    return super.evaluateReport(userId, reportDefinition);
  }

  @Autowired
  private SessionService sessionService;

  protected boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto report) {
    if (report instanceof SingleProcessReportDefinitionDto) {
      SingleProcessReportDefinitionDto processReport = (SingleProcessReportDefinitionDto) report;
      ProcessReportDataDto reportData = processReport.getData();
      if (reportData != null) {
        return sessionService.isAuthorizedToSeeProcessDefinition(userId, reportData.getProcessDefinitionKey());
      }
    } else if (report instanceof SingleDecisionReportDefinitionDto) {
      SingleDecisionReportDefinitionDto decisionReport = (SingleDecisionReportDefinitionDto) report;
      DecisionReportDataDto reportData = decisionReport.getData();
      if (reportData != null) {
        return sessionService.isAuthorizedToSeeDecisionDefinition(userId, reportData.getDecisionDefinitionKey());
      }
    }
    return true;
  }
}
