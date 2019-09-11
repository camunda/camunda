/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.springframework.stereotype.Component;

@Component
public class PlainReportEvaluationHandler extends ReportEvaluationHandler {


  public PlainReportEvaluationHandler(ReportReader reportReader, SingleReportEvaluator singleReportEvaluator,
                                      CombinedReportEvaluator combinedReportEvaluator) {
    super(reportReader, singleReportEvaluator, combinedReportEvaluator);
  }

  public ReportEvaluationResult evaluateSavedReport(String reportId) {
    return this.evaluateSavedReport(null, reportId);
  }

  public ReportEvaluationResult evaluateReport(ReportDefinitionDto reportDefinition) {
    return this.evaluateReport(null, reportDefinition);
  }

  @Override
  protected boolean isAuthorizedToAccessReport(String userId, ReportDefinitionDto report) {
    return true;
  }
}
