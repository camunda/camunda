package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class AuthorizationCheckReportEvaluationHandler extends ReportEvaluationHandler {

  private final DefinitionAuthorizationService authorizationService;

  @Autowired
  public AuthorizationCheckReportEvaluationHandler(final ReportReader reportReader,
                                                   final SingleReportEvaluator singleReportEvaluator,
                                                   final CombinedReportEvaluator combinedReportEvaluator,
                                                   final DefinitionAuthorizationService authorizationService) {
    super(reportReader, singleReportEvaluator, combinedReportEvaluator);
    this.authorizationService = authorizationService;
  }

  public ReportEvaluationResult evaluateSavedReport(String userId, String reportId) {
    return super.evaluateSavedReport(userId, reportId);
  }

  public ReportEvaluationResult evaluateReport(String userId, ReportDefinitionDto reportDefinition) {
    return super.evaluateReport(userId, reportDefinition);
  }

  protected boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto report) {
    if (report instanceof SingleProcessReportDefinitionDto) {
      SingleProcessReportDefinitionDto processReport = (SingleProcessReportDefinitionDto) report;
      ProcessReportDataDto reportData = processReport.getData();
      if (reportData != null) {
        return authorizationService.isAuthorizedToSeeProcessDefinition(userId, reportData.getProcessDefinitionKey());
      }
    } else if (report instanceof SingleDecisionReportDefinitionDto) {
      SingleDecisionReportDefinitionDto decisionReport = (SingleDecisionReportDefinitionDto) report;
      DecisionReportDataDto reportData = decisionReport.getData();
      if (reportData != null) {
        return authorizationService.isAuthorizedToSeeDecisionDefinition(userId, reportData.getDecisionDefinitionKey());
      }
    }
    return true;
  }
}
