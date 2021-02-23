/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.service.es.report.result.process.CombinedProcessReportResult;
import org.camunda.optimize.service.identity.IdentityService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class ReportRestMapper {

  private final IdentityService identityService;

  public AuthorizedReportEvaluationResponseDto<?> mapToEvaluationResultDto(final AuthorizedReportEvaluationResult reportEvaluationResult) {
    resolveOwnerAndModifierNames(reportEvaluationResult.getEvaluationResult().getReportDefinition());
    if (reportEvaluationResult.getEvaluationResult() instanceof CombinedProcessReportResult) {
      final CombinedProcessReportResult combinedReportResult =
        (CombinedProcessReportResult) reportEvaluationResult.getEvaluationResult();
      final CombinedProcessReportResultDto<?> resultAsDto = combinedReportResult.getResultAsDto();

      final Map<String, AuthorizedSingleReportEvaluationResponseDto<?, ?>> results = resultAsDto.getData()
        .entrySet().stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> new AuthorizedProcessReportEvaluationResponseDto<>(
            null,
            mapToReportResultResponseDto(entry.getValue()),
            entry.getValue().getReportDefinition()
          ),
          (x, y) -> y,
          LinkedHashMap::new
        ));
      return new AuthorizedCombinedReportEvaluationResponseDto<>(
        reportEvaluationResult.getCurrentUserRole(),
        combinedReportResult.getReportDefinition(),
        new CombinedProcessReportResultDataDto(results, resultAsDto.getInstanceCount())
      );
    } else {
      return mapToAuthorizedEvaluationResponseDto(reportEvaluationResult);
    }
  }

  private AuthorizedSingleReportEvaluationResponseDto<?, ?> mapToAuthorizedEvaluationResponseDto(
    final AuthorizedReportEvaluationResult reportEvaluationResult) {
    return new AuthorizedSingleReportEvaluationResponseDto<>(
      reportEvaluationResult.getCurrentUserRole(),
      mapToReportResultResponseDto(reportEvaluationResult.getEvaluationResult()),
      reportEvaluationResult.getEvaluationResult().getReportDefinition()
    );
  }

  private ReportResultResponseDto<?> mapToReportResultResponseDto(final ReportEvaluationResult<?, ?> evaluationResult) {
    final SingleReportResultDto resultAsDto = (SingleReportResultDto) evaluationResult.getResultAsDto();
    return new ReportResultResponseDto<>(
      resultAsDto.getInstanceCount(),
      resultAsDto.getInstanceCountWithoutFilters(),
      resultAsDto.getMeasures().stream()
        .map(measureDto ->
               new MeasureResponseDto<>(
                 measureDto.getProperty(),
                 measureDto.getAggregationType(),
                 measureDto.getUserTaskDurationTime(),
                 measureDto.getData(),
                 resultAsDto.getType()
               )
        ).collect(Collectors.toList()),
      extractPagination(resultAsDto)
    );
  }

  private PaginationDto extractPagination(final SingleReportResultDto resultDto) {
    if (resultDto instanceof RawDataProcessReportResultDto) {
      return ((RawDataProcessReportResultDto) resultDto).getPagination();
    } else if (resultDto instanceof RawDataDecisionReportResultDto) {
      return ((RawDataDecisionReportResultDto) resultDto).getPagination();
    } else {
      return null;
    }
  }

  public void prepareRestResponse(final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto) {
    resolveOwnerAndModifierNames(authorizedReportDefinitionDto.getDefinitionDto());
  }

  private void resolveOwnerAndModifierNames(ReportDefinitionDto<?> reportDefinitionDto) {
    Optional.ofNullable(reportDefinitionDto.getOwner())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(reportDefinitionDto::setOwner);
    Optional.ofNullable(reportDefinitionDto.getLastModifier())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(reportDefinitionDto::setLastModifier);
  }
}
