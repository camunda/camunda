/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.mapper;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
public class ReportRestMapper {

  private final AbstractIdentityService identityService;
  private final LocalizationService localizationService;

  public <T> AuthorizedReportEvaluationResponseDto<?> mapToLocalizedEvaluationResponseDto(
      final AuthorizedReportEvaluationResult reportEvaluationResult, final String locale) {
    return mapToLocalizedEvaluationResponseDto(reportEvaluationResult, locale, false);
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public <T> AuthorizedReportEvaluationResponseDto<?> mapToLocalizedEvaluationResponseDto(
      final AuthorizedReportEvaluationResult reportEvaluationResult,
      final String locale,
      final boolean skipNameResolution) {
    if (!skipNameResolution) {
      resolveOwnerAndModifierNames(
          reportEvaluationResult.getEvaluationResult().getReportDefinition());
    }
    final SingleReportEvaluationResult<?> singleReportEvaluationResult =
        (SingleReportEvaluationResult<?>) reportEvaluationResult.getEvaluationResult();
    return mapToLocalizedEvaluationResponseDto(
        reportEvaluationResult.getCurrentUserRole(), singleReportEvaluationResult, locale);
  }

  public void prepareLocalizedRestResponse(
      final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto,
      final String locale) {
    resolveOwnerAndModifierNames(authorizedReportDefinitionDto.getDefinitionDto());
    localizeReportData(authorizedReportDefinitionDto.getDefinitionDto(), locale);
  }

  public static void localizeReportData(
      final ReportDefinitionDto<?> reportDefinitionDto,
      final String locale,
      final LocalizationService localizationService) {
    if (isManagementOrInstantPreviewReport(reportDefinitionDto)) {
      final String validLocale = localizationService.validateAndReturnValidLocale(locale);
      if (((ProcessReportDefinitionRequestDto) reportDefinitionDto)
          .getData()
          .isManagementReport()) {
        Optional.ofNullable(
                localizationService.getLocalizationForManagementReportCode(
                    validLocale, reportDefinitionDto.getName()))
            .ifPresent(reportDefinitionDto::setName);
        Optional.ofNullable(
                localizationService.getLocalizationForManagementReportCode(
                    validLocale, reportDefinitionDto.getDescription()))
            .ifPresent(reportDefinitionDto::setDescription);
      } else {
        Optional.ofNullable(
                localizationService.getLocalizationForInstantPreviewReportCode(
                    validLocale, reportDefinitionDto.getName()))
            .ifPresent(reportDefinitionDto::setName);
        Optional.ofNullable(
                localizationService.getLocalizationForInstantPreviewReportCode(
                    validLocale, reportDefinitionDto.getDescription()))
            .ifPresent(reportDefinitionDto::setDescription);
      }
      localizeChartLabels(reportDefinitionDto.getData(), localizationService, validLocale);
    }
  }

  private static void localizeChartLabels(
      final ReportDataDto reportDataDto,
      final LocalizationService localizationService,
      final String validLocale) {
    if (reportDataDto.getConfiguration() != null) {
      Optional.ofNullable(reportDataDto.getConfiguration().getXLabel())
          .map(xLabel -> localizationService.getLocalizedXLabel(validLocale, xLabel))
          .ifPresent(localizedLabel -> reportDataDto.getConfiguration().setXLabel(localizedLabel));
      if (reportDataDto instanceof final ProcessReportDataDto processReportData) {
        Optional.ofNullable(processReportData.getConfiguration().getYLabel())
            .map(
                yLabel ->
                    localizationService.getLocalizedYLabel(
                        validLocale, yLabel, processReportData.getView().getFirstProperty()))
            .ifPresent(
                localizedLabel -> reportDataDto.getConfiguration().setYLabel(localizedLabel));
      }
    }
  }

  private <T, R extends ReportDefinitionDto<?>>
      AuthorizedSingleReportEvaluationResponseDto<T, R> mapToLocalizedEvaluationResponseDto(
          final RoleType currentUserRole,
          final SingleReportEvaluationResult<?> evaluationResult,
          final String locale) {
    final AuthorizedSingleReportEvaluationResponseDto<T, R> mappedResult =
        new AuthorizedSingleReportEvaluationResponseDto<>(
            currentUserRole,
            (ReportResultResponseDto<T>) mapToReportResultResponseDto(evaluationResult),
            (R) evaluationResult.getReportDefinition());
    localizeReportData(mappedResult.getReportDefinition(), locale);
    return mappedResult;
  }

  private <T> ReportResultResponseDto<T> mapToReportResultResponseDto(
      final SingleReportEvaluationResult<T> evaluationResult) {
    final CommandEvaluationResult<?> firstCommandResult = evaluationResult.getFirstCommandResult();
    return new ReportResultResponseDto<>(
        firstCommandResult.getInstanceCount(),
        firstCommandResult.getInstanceCountWithoutFilters(),
        evaluationResult.getCommandEvaluationResults().stream()
            .flatMap(
                commandResult ->
                    commandResult.getMeasures().stream()
                        .map(
                            measureDto ->
                                new MeasureResponseDto<>(
                                    measureDto.getProperty(),
                                    measureDto.getAggregationType(),
                                    measureDto.getUserTaskDurationTime(),
                                    measureDto.getData(),
                                    commandResult.getType())))
            .collect(Collectors.toList()),
        firstCommandResult.getPagination().isValid() ? firstCommandResult.getPagination() : null);
  }

  private void resolveOwnerAndModifierNames(final ReportDefinitionDto<?> reportDefinitionDto) {
    Optional.ofNullable(reportDefinitionDto.getOwner())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(reportDefinitionDto::setOwner);
    Optional.ofNullable(reportDefinitionDto.getLastModifier())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(reportDefinitionDto::setLastModifier);
  }

  private static boolean isManagementOrInstantPreviewReport(
      final ReportDefinitionDto<?> reportDefinitionDto) {
    return reportDefinitionDto instanceof ProcessReportDefinitionRequestDto
        && (((ProcessReportDefinitionRequestDto) reportDefinitionDto).getData().isManagementReport()
            || ((ProcessReportDefinitionRequestDto) reportDefinitionDto)
                .getData()
                .isInstantPreviewReport());
  }

  private void localizeReportData(
      final ReportDefinitionDto<?> reportDefinitionDto, final String locale) {
    localizeReportData(reportDefinitionDto, locale, localizationService);
  }
}
