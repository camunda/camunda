/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.EvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.es.report.result.process.CombinedProcessReportResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportEvaluationResultMapper {

  private ReportEvaluationResultMapper() {
  }

  @SuppressWarnings("unchecked")
  public static EvaluationResultDto<?, ?> mapToEvaluationResultDto(final ReportEvaluationResult reportEvaluationResult) {
    if (reportEvaluationResult instanceof CombinedProcessReportResult) {
      final CombinedProcessReportResult combinedReportResult = (CombinedProcessReportResult) reportEvaluationResult;
      final CombinedProcessReportResultDto<?> resultAsDto = combinedReportResult.getResultAsDto();

      final Map<String, EvaluationResultDto> results = resultAsDto.getData()
        .entrySet().stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> new ProcessReportEvaluationResultDto(
            entry.getValue().getResultAsDto(), entry.getValue().getReportDefinition()
          ),
          (x, y) -> y,
          LinkedHashMap::new
        ));
      return new CombinedReportEvaluationResultDto(
        new CombinedProcessReportResultDataDto(results),
        combinedReportResult.getReportDefinition()
      );
    } else {
      return EvaluationResultDto.from(reportEvaluationResult);
    }
  }
}
