/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.CombinedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.util.SuppressionConstants;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class ReportRestMapper {

  private final AbstractIdentityService identityService;

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public <T> AuthorizedReportEvaluationResponseDto<?> mapToEvaluationResultDto(final AuthorizedReportEvaluationResult reportEvaluationResult) {
    resolveOwnerAndModifierNames(reportEvaluationResult.getEvaluationResult().getReportDefinition());
    if (reportEvaluationResult.getEvaluationResult() instanceof CombinedReportEvaluationResult) {
      final CombinedReportEvaluationResult combinedReportEvaluationResult =
        (CombinedReportEvaluationResult) reportEvaluationResult.getEvaluationResult();
      final Map<String, AuthorizedProcessReportEvaluationResponseDto<T>> reportResults =
        combinedReportEvaluationResult
          .getReportEvaluationResults()
          .stream()
          .map(this::mapToAuthorizedProcessReportEvaluationResponseDto)
          .map(response -> (AuthorizedProcessReportEvaluationResponseDto<T>) response)
          .collect(Collectors.toMap(
            singleReportEvaluationResponse -> singleReportEvaluationResponse.getReportDefinition().getId(),
            Function.identity(),
            (x, y) -> y,
            LinkedHashMap::new
          ));

      return new AuthorizedCombinedReportEvaluationResponseDto<>(
        reportEvaluationResult.getCurrentUserRole(),
        (CombinedReportDefinitionRequestDto) reportEvaluationResult.getEvaluationResult().getReportDefinition(),
        new CombinedProcessReportResultDataDto<>(reportResults, combinedReportEvaluationResult.getInstanceCount())
      );
    } else {
      SingleReportEvaluationResult<?> singleReportEvaluationResult =
        (SingleReportEvaluationResult<?>) reportEvaluationResult.getEvaluationResult();
      return mapToAuthorizedEvaluationResponseDto(
        reportEvaluationResult.getCurrentUserRole(), singleReportEvaluationResult
      );
    }
  }

  private <T> AuthorizedProcessReportEvaluationResponseDto<T> mapToAuthorizedProcessReportEvaluationResponseDto(
    final SingleReportEvaluationResult<T> singleReportEvaluationResult) {
    return new AuthorizedProcessReportEvaluationResponseDto<>(
      null,
      mapToReportResultResponseDto(singleReportEvaluationResult),
      (SingleProcessReportDefinitionRequestDto) singleReportEvaluationResult.getReportDefinition()
    );
  }

  private <T, R extends ReportDefinitionDto<?>> AuthorizedSingleReportEvaluationResponseDto<T, R> mapToAuthorizedEvaluationResponseDto(
    final RoleType currentUserRole,
    final SingleReportEvaluationResult<?> evaluationResult) {
    return new AuthorizedSingleReportEvaluationResponseDto<>(
      currentUserRole,
      (ReportResultResponseDto<T>) mapToReportResultResponseDto(evaluationResult),
      (R) evaluationResult.getReportDefinition()
    );
  }

  private <T> ReportResultResponseDto<T> mapToReportResultResponseDto(final SingleReportEvaluationResult<T> evaluationResult) {
    final CommandEvaluationResult<?> firstCommandResult = evaluationResult.getFirstCommandResult();
    return new ReportResultResponseDto<>(
      firstCommandResult.getInstanceCount(),
      firstCommandResult.getInstanceCountWithoutFilters(),
      evaluationResult.getCommandEvaluationResults().stream()
        .flatMap(commandResult -> commandResult.getMeasures().stream()
          .map(measureDto ->
                 new MeasureResponseDto<>(
                   measureDto.getProperty(),
                   measureDto.getAggregationType(),
                   measureDto.getUserTaskDurationTime(),
                   measureDto.getData(),
                   commandResult.getType()
                 )
          ))
        .collect(Collectors.toList()),
      firstCommandResult.getPagination().isValid() ? firstCommandResult.getPagination() : null
    );
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
