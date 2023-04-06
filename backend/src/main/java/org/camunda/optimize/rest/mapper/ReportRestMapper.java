/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.util.SuppressionConstants;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Component
public class ReportRestMapper {

  private final AbstractIdentityService identityService;
  private final LocalizationService localizationService;

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public <T> AuthorizedReportEvaluationResponseDto<?> mapToLocalizedEvaluationResponseDto(
    final AuthorizedReportEvaluationResult reportEvaluationResult,
    final String locale) {
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
      return mapToLocalizedEvaluationResponseDto(
        reportEvaluationResult.getCurrentUserRole(), singleReportEvaluationResult, locale
      );
    }
  }

  public void prepareRestResponse(final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto) {
    resolveOwnerAndModifierNames(authorizedReportDefinitionDto.getDefinitionDto());
  }

  public static void localizeReportNames(final ReportDefinitionDto<?> reportDefinitionDto, final String locale,
                                         LocalizationService localizationService) {
    if (isManagementOrInstantPreviewReport(reportDefinitionDto)) {
      final String validLocale = localizationService.validateAndReturnValidLocale(locale);
      if (((SingleProcessReportDefinitionRequestDto) reportDefinitionDto).getData().isManagementReport()) {
        Optional.ofNullable(localizationService.getLocalizationForManagementReportCode(
          validLocale,
          reportDefinitionDto.getName()
        )).ifPresent(reportDefinitionDto::setName);
      } else {
        Optional.ofNullable(localizationService.getLocalizationForInstantPreviewReportCode(
          validLocale,
          reportDefinitionDto.getName()
        )).ifPresent(reportDefinitionDto::setName);
      }
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

  private <T, R extends ReportDefinitionDto<?>> AuthorizedSingleReportEvaluationResponseDto<T, R> mapToLocalizedEvaluationResponseDto(
    final RoleType currentUserRole,
    final SingleReportEvaluationResult<?> evaluationResult,
    final String locale) {
    final AuthorizedSingleReportEvaluationResponseDto<T, R> mappedResult = new AuthorizedSingleReportEvaluationResponseDto<>(
      currentUserRole,
      (ReportResultResponseDto<T>) mapToReportResultResponseDto(evaluationResult),
      (R) evaluationResult.getReportDefinition()
    );
    localizeReportNames(mappedResult.getReportDefinition(), locale, localizationService);
    return mappedResult;
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

  private void resolveOwnerAndModifierNames(ReportDefinitionDto<?> reportDefinitionDto) {
    // We are assuming that if the owner/last modifier field contains empty spaces, that we already have the
    // display name (and not the user ID), therefore no transformation necessary, so we're filtering it out
    Optional.ofNullable(reportDefinitionDto.getOwner())
      .filter(owner -> !owner.contains(" "))
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(reportDefinitionDto::setOwner);
    Optional.ofNullable(reportDefinitionDto.getLastModifier())
      .filter(lastModifier-> !lastModifier.contains(" "))
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(reportDefinitionDto::setLastModifier);
  }

  private static boolean isManagementOrInstantPreviewReport(final ReportDefinitionDto<?> reportDefinitionDto) {
    return reportDefinitionDto instanceof SingleProcessReportDefinitionRequestDto
      && (((SingleProcessReportDefinitionRequestDto) reportDefinitionDto).getData().isManagementReport()
      || ((SingleProcessReportDefinitionRequestDto) reportDefinitionDto).getData().isInstantPreviewReport());
  }
}
