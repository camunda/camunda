/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;

import java.time.ZoneId;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Data
public class ReportEvaluationContext<R extends ReportDefinitionDto<?>> {

  private R reportDefinition;
  private PaginationDto pagination;
  private boolean isExport;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // users can define which timezone the date data should be based on
  private ZoneId timezone = ZoneId.systemDefault();

  @SuppressWarnings(UNCHECKED_CAST)
  public static <R extends ReportDefinitionDto<?>> ReportEvaluationContext<R> fromReportEvaluation(final ReportEvaluationInfo evaluationInfo) {
    ReportEvaluationContext<R> context = new ReportEvaluationContext<>();
    context.setReportDefinition((R) evaluationInfo.getReport());
    context.setTimezone(evaluationInfo.getTimezone());
    context.setPagination(evaluationInfo.getPagination());
    context.setExport(evaluationInfo.isExport());
    return context;
  }

}
