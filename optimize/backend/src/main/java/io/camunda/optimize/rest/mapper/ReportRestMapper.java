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
import io.camunda.optimize.dto.optimize.query.report.CombinedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ReportRestMapper {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ReportRestMapper.class);
  private final AbstractIdentityService identityService;
  private final LocalizationService localizationService;

  public ReportRestMapper(
      final AbstractIdentityService identityService,
      final LocalizationService localizationService) {
    this.identityService = identityService;
    this.localizationService = localizationService;
  }

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
    if (reportEvaluationResult.getEvaluationResult()
        instanceof final CombinedReportEvaluationResult combinedReportEvaluationResult) {
      final Map<String, AuthorizedProcessReportEvaluationResponseDto<T>> reportResults =
          combinedReportEvaluationResult.getReportEvaluationResults().stream()
              .map(this::mapToAuthorizedProcessReportEvaluationResponseDto)
              .map(response -> (AuthorizedProcessReportEvaluationResponseDto<T>) response)
              .collect(
                  Collectors.toMap(
                      singleReportEvaluationResponse ->
                          singleReportEvaluationResponse.getReportDefinition().getId(),
                      Function.identity(),
                      (x, y) -> y,
                      LinkedHashMap::new));

      return new AuthorizedCombinedReportEvaluationResponseDto<>(
          reportEvaluationResult.getCurrentUserRole(),
          (CombinedReportDefinitionRequestDto)
              reportEvaluationResult.getEvaluationResult().getReportDefinition(),
          new CombinedProcessReportResultDataDto<>(
              reportResults, combinedReportEvaluationResult.getInstanceCount()));
    } else {
      final SingleReportEvaluationResult<?> singleReportEvaluationResult =
          (SingleReportEvaluationResult<?>) reportEvaluationResult.getEvaluationResult();
      return mapToLocalizedEvaluationResponseDto(
          reportEvaluationResult.getCurrentUserRole(), singleReportEvaluationResult, locale);
    }
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
    if (isSystemGeneratedReport(reportDefinitionDto)) {
      final String validLocale = localizationService.validateAndReturnValidLocale(locale);
      final var data = ((SingleProcessReportDefinitionRequestDto) reportDefinitionDto).getData();
      final BiFunction<String, String, String> localizer =
          resolveSystemReportLocalizer(data, localizationService);
      Optional.ofNullable(localizer.apply(validLocale, reportDefinitionDto.getName()))
          .ifPresent(reportDefinitionDto::setName);
      Optional.ofNullable(localizer.apply(validLocale, reportDefinitionDto.getDescription()))
          .ifPresent(reportDefinitionDto::setDescription);
      localizeConfigurationSubtitle(data, localizer, validLocale);
      localizeChartLabels(reportDefinitionDto, localizationService, validLocale);
    }
  }

  private static BiFunction<String, String, String> resolveSystemReportLocalizer(
      final ProcessReportDataDto data, final LocalizationService localizationService) {
    if (data.isManagementReport()) {
      return localizationService::getLocalizationForManagementReportCode;
    } else if (data.isAgenticControlReport()) {
      return localizationService::getLocalizationForAgenticControlReportCode;
    } else {
      return localizationService::getLocalizationForInstantPreviewReportCode;
    }
  }

  private static void localizeConfigurationSubtitle(
      final ProcessReportDataDto data,
      final BiFunction<String, String, String> localizer,
      final String validLocale) {
    Optional.ofNullable(data.getConfiguration())
        .map(SingleReportConfigurationDto::getSubtitle)
        .filter(subtitle -> !subtitle.isBlank())
        .map(subtitle -> localizer.apply(validLocale, subtitle))
        .ifPresent(localized -> data.getConfiguration().setSubtitle(localized));
  }

  private static void localizeChartLabels(
      final ReportDefinitionDto<?> reportDefinitionDto,
      final LocalizationService localizationService,
      final String validLocale) {
    if (reportDefinitionDto.getData() instanceof final SingleReportDataDto reportDataDto
        && (reportDataDto.getConfiguration() != null)) {
      Optional.ofNullable(reportDataDto.getConfiguration().getXLabel())
          .map(xLabel -> localizationService.getLocalizedXLabel(validLocale, xLabel))
          .ifPresent(
              localizedLabel ->
                  ((SingleReportDataDto) reportDefinitionDto.getData())
                      .getConfiguration()
                      .setXLabel(localizedLabel));
      if (reportDataDto instanceof final ProcessReportDataDto processReportData) {
        Optional.ofNullable(processReportData.getConfiguration().getYLabel())
            .map(
                yLabel ->
                    localizationService.getLocalizedYLabel(
                        validLocale, yLabel, processReportData.getView().getFirstProperty()))
            .ifPresent(
                localizedLabel ->
                    ((SingleReportDataDto) reportDefinitionDto.getData())
                        .getConfiguration()
                        .setYLabel(localizedLabel));
      }
    }
  }

  private <T>
      AuthorizedProcessReportEvaluationResponseDto<T>
          mapToAuthorizedProcessReportEvaluationResponseDto(
              final SingleReportEvaluationResult<T> singleReportEvaluationResult) {
    return new AuthorizedProcessReportEvaluationResponseDto<>(
        null,
        mapToReportResultResponseDto(singleReportEvaluationResult),
        (SingleProcessReportDefinitionRequestDto)
            singleReportEvaluationResult.getReportDefinition());
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

  private static boolean isSystemGeneratedReport(final ReportDefinitionDto<?> reportDefinitionDto) {
    if (!(reportDefinitionDto instanceof SingleProcessReportDefinitionRequestDto)) {
      return false;
    }
    return ((SingleProcessReportDefinitionRequestDto) reportDefinitionDto)
        .getData()
        .isSystemGeneratedReport();
  }

  private void localizeReportData(
      final ReportDefinitionDto<?> reportDefinitionDto, final String locale) {
    localizeReportData(reportDefinitionDto, locale, localizationService);
  }
}
