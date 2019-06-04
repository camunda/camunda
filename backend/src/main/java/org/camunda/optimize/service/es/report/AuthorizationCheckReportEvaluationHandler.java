/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.springframework.stereotype.Component;


@Component
public class AuthorizationCheckReportEvaluationHandler extends ReportEvaluationHandler {

  private final ReportAuthorizationService authorizationService;

  public AuthorizationCheckReportEvaluationHandler(final ReportReader reportReader,
                                                   final SingleReportEvaluator singleReportEvaluator,
                                                   final CombinedReportEvaluator combinedReportEvaluator,
                                                   final ReportAuthorizationService authorizationService) {
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
        return authorizationService.isAuthorizedToSeeProcessReport(
          userId, reportData.getProcessDefinitionKey(), reportData.getTenantIds()
        );
      }
    } else if (report instanceof SingleDecisionReportDefinitionDto) {
      SingleDecisionReportDefinitionDto decisionReport = (SingleDecisionReportDefinitionDto) report;
      DecisionReportDataDto reportData = decisionReport.getData();
      if (reportData != null) {
        return authorizationService.isAuthorizedToSeeDecisionReport(
          userId, reportData.getDecisionDefinitionKey(), reportData.getTenantIds()
        );
      }
    }
    return true;
  }
}
