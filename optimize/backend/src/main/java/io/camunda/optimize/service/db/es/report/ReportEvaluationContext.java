/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import java.time.ZoneId;
import java.util.Optional;
import lombok.Data;

@Data
public class ReportEvaluationContext<R extends ReportDefinitionDto<?>> {

  private R reportDefinition;
  private PaginationDto pagination;
  private boolean isCsvExport;
  private boolean isJsonExport;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all
  // single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // users can define which timezone the date data should be based on
  private ZoneId timezone = ZoneId.systemDefault();

  public Optional<PaginationDto> getPagination() {
    return Optional.ofNullable(pagination);
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public static <R extends ReportDefinitionDto<?>> ReportEvaluationContext<R> fromReportEvaluation(
      final ReportEvaluationInfo evaluationInfo) {
    ReportEvaluationContext<R> context = new ReportEvaluationContext<>();
    context.setReportDefinition((R) evaluationInfo.getReport());
    context.setTimezone(evaluationInfo.getTimezone());
    context.setPagination(evaluationInfo.getPagination().orElse(null));
    context.setCsvExport(evaluationInfo.isCsvExport());
    context.setJsonExport(evaluationInfo.isJsonExport());
    return context;
  }
}
