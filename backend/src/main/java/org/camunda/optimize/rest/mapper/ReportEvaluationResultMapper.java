/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.mapper;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.service.es.report.result.process.CombinedProcessReportResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportEvaluationResultMapper {

  private ReportEvaluationResultMapper() {
  }

  @SuppressWarnings("unchecked")
  public static AuthorizedEvaluationResultDto<?, ?> mapToEvaluationResultDto(final AuthorizedReportEvaluationResult reportEvaluationResult) {
    if (reportEvaluationResult.getEvaluationResult() instanceof CombinedProcessReportResult) {
      final CombinedProcessReportResult combinedReportResult =
        (CombinedProcessReportResult) reportEvaluationResult.getEvaluationResult();
      final CombinedProcessReportResultDto<?> resultAsDto = combinedReportResult.getResultAsDto();

      final Map<String, AuthorizedEvaluationResultDto> results = resultAsDto.getData()
        .entrySet().stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> new AuthorizedProcessReportEvaluationResultDto(
            entry.getValue().getResultAsDto(),
            entry.getValue().getReportDefinition()
          ),
          (x, y) -> y,
          LinkedHashMap::new
        ));
      return new AuthorizedCombinedReportEvaluationResultDto(
        reportEvaluationResult.getCurrentUserRole(),
        new CombinedProcessReportResultDataDto(results),
        combinedReportResult.getReportDefinition()
      );
    } else {
      return AuthorizedEvaluationResultDto.from(reportEvaluationResult);
    }
  }
}
