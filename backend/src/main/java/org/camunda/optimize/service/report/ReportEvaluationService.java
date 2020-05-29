/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportEvaluationService {

  private final AuthorizationCheckReportEvaluationHandler reportEvaluator;

  public AuthorizedReportEvaluationResult evaluateSavedReportWithAdditionalFilters(final String userId,
                                                                                   final String reportId,
                                                                                   final AdditionalProcessReportEvaluationFilterDto filterDto) {
    // auth is handled in evaluator as it also handles single reports of a combined report
    return reportEvaluator.evaluateSavedReportWithAdditionalFilters(userId, reportId, filterDto);
  }

  public AuthorizedReportEvaluationResult evaluateReport(final String userId,
                                                         final ReportDefinitionDto reportDefinition) {
    // auth is handled in evaluator as it also handles single reports of a combined report
    return reportEvaluator.evaluateReport(userId, reportDefinition);
  }

}