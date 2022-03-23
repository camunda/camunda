/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;

import java.time.ZoneId;
import java.util.Optional;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Data
public class ReportEvaluationContext<R extends ReportDefinitionDto<?>> {

  private R reportDefinition;
  private PaginationDto pagination;
  private boolean isCsvExport;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // users can define which timezone the date data should be based on
  private ZoneId timezone = ZoneId.systemDefault();

  public Optional<PaginationDto> getPagination()
  {
    return Optional.ofNullable(pagination);
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public static <R extends ReportDefinitionDto<?>> ReportEvaluationContext<R> fromReportEvaluation(final ReportEvaluationInfo evaluationInfo) {
    ReportEvaluationContext<R> context = new ReportEvaluationContext<>();
    context.setReportDefinition((R) evaluationInfo.getReport());
    context.setTimezone(evaluationInfo.getTimezone());
    context.setPagination(evaluationInfo.getPagination().orElse(null));
    context.setCsvExport(evaluationInfo.isCsvExport());
    return context;
  }

}
