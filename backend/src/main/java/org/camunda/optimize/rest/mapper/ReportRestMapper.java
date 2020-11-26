/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.mapper;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.report.result.process.CombinedProcessReportResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Component
public class ReportRestMapper {

  private final IdentityService identityService;

  private ReportRestMapper(final IdentityService identityService) {
    this.identityService = identityService;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public AuthorizedEvaluationResultDto<?, ?> mapToEvaluationResultDto(final AuthorizedReportEvaluationResult reportEvaluationResult) {
    resolveOwnerAndModifierNames(reportEvaluationResult.getEvaluationResult().getReportDefinition());
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
        new CombinedProcessReportResultDataDto(results, resultAsDto.getInstanceCount()),
        combinedReportResult.getReportDefinition()
      );
    } else {
      return AuthorizedEvaluationResultDto.from(reportEvaluationResult);
    }
  }

  public void prepareRestResponse(final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto) {
    resolveOwnerAndModifierNames(authorizedReportDefinitionDto.getDefinitionDto());
  }

  private void resolveOwnerAndModifierNames(ReportDefinitionDto reportDefinitionDto) {
    Optional.ofNullable(reportDefinitionDto.getOwner())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(reportDefinitionDto::setOwner);
    Optional.ofNullable(reportDefinitionDto.getLastModifier())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(reportDefinitionDto::setLastModifier);
  }
}
