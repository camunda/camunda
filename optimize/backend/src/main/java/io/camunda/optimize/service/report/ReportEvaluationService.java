/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.es.report.AuthorizationCheckReportEvaluationHandler;
import io.camunda.optimize.service.db.es.report.ReportEvaluationInfo;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ReportEvaluationService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ReportEvaluationService.class);
  private final AuthorizationCheckReportEvaluationHandler reportEvaluator;

  public ReportEvaluationService(final AuthorizationCheckReportEvaluationHandler reportEvaluator) {
    this.reportEvaluator = reportEvaluator;
  }

  public AuthorizedReportEvaluationResult evaluateSavedReportWithAdditionalFilters(
      final String userId,
      final ZoneId timezone,
      final String reportId,
      final AdditionalProcessReportEvaluationFilterDto filterDto,
      final PaginationDto paginationDto) {
    final ReportEvaluationInfo evaluationInfo =
        ReportEvaluationInfo.builder(reportId)
            .userId(userId)
            .timezone(timezone)
            .pagination(paginationDto)
            .additionalFilters(filterDto)
            .build();
    // auth is handled in evaluator as it also handles single reports of a combined report
    return reportEvaluator.evaluateReport(evaluationInfo);
  }

  public AuthorizedReportEvaluationResult evaluateUnsavedReport(
      final String userId,
      final ZoneId timezone,
      final ReportDefinitionDto reportDefinition,
      final PaginationDto paginationDto) {
    // reset owner and last modifier to avoid unnecessary user retrieval hits when resolving to
    // display names during rest mapping
    // as no owner/modifier display names are required for unsaved reports
    reportDefinition.setOwner(null);
    reportDefinition.setLastModifier(null);
    final ReportEvaluationInfo evaluationInfo =
        ReportEvaluationInfo.builder(reportDefinition)
            .userId(userId)
            .timezone(timezone)
            .pagination(paginationDto)
            .build();
    // auth is handled in evaluator as it also handles single reports of a combined report
    return reportEvaluator.evaluateReport(evaluationInfo);
  }
}
