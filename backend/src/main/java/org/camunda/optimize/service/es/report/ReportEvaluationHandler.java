/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.process.CombinedProcessReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.variable.ProcessVariableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;

@RequiredArgsConstructor
@Component
public abstract class ReportEvaluationHandler {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final ReportReader reportReader;
  private final SingleReportEvaluator singleReportEvaluator;
  private final CombinedReportEvaluator combinedReportEvaluator;
  private final ProcessVariableService processVariableService;

  public AuthorizedReportEvaluationResult evaluateSavedReport(final String userId,
                                                              final String reportId) {
    return evaluateSavedReport(userId, reportId, null);
  }

  public AuthorizedReportEvaluationResult evaluateSavedReport(final String userId,
                                                              final String reportId,
                                                              final Integer customRecordLimit) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    return evaluateReport(userId, reportDefinition, customRecordLimit);
  }

  public AuthorizedReportEvaluationResult evaluateSavedReportWithAdditionalFilters(final String userId,
                                                                                   final String reportId,
                                                                                   final AdditionalProcessReportEvaluationFilterDto filterDto) {
    ReportDefinitionDto<?> reportDefinition = reportReader.getReport(reportId);
    return evaluateReport(userId, reportDefinition, null, filterDto);
  }

  public AuthorizedReportEvaluationResult evaluateReport(final String userId,
                                                         final ReportDefinitionDto reportDefinition) {
    return evaluateReport(userId, reportDefinition, null);
  }

  public AuthorizedReportEvaluationResult evaluateReport(final String userId,
                                                         final ReportDefinitionDto report,
                                                         final Integer customRecordLimit) {
    return evaluateReport(userId, report, customRecordLimit, null);
  }

  public AuthorizedReportEvaluationResult evaluateReport(final String userId,
                                                         final ReportDefinitionDto report,
                                                         final Integer customRecordLimit,
                                                         final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final RoleType currentUserRole = getAuthorizedRole(userId, report)
      .orElseThrow(() -> new ForbiddenException(String.format(
        "User [%s] is not authorized to evaluate report [%s].", userId, report.getName()
      )));
    final AuthorizedReportDefinitionDto authorizedReportDefinitionDto = new AuthorizedReportDefinitionDto(
      report, currentUserRole
    );
    final ReportEvaluationResult result;
    if (!report.getCombined()) {
      result = evaluateSingleReportWithErrorCheck(userId, authorizedReportDefinitionDto, customRecordLimit, filterDto);
    } else {
      result = evaluateCombinedReport(userId, authorizedReportDefinitionDto, filterDto);
    }
    return new AuthorizedReportEvaluationResult(result, currentUserRole);
  }

  private CombinedProcessReportResult evaluateCombinedReport(final String userId,
                                                             final AuthorizedReportDefinitionDto authorizedReportDefinitionDto,
                                                             final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final CombinedReportDefinitionDto combinedReportDefinitionDto =
      (CombinedReportDefinitionDto) authorizedReportDefinitionDto.getDefinitionDto();
    ValidationHelper.validateCombinedReportDefinition(authorizedReportDefinitionDto);
    List<ReportEvaluationResult> resultList = evaluateListOfReportIds(
      userId, combinedReportDefinitionDto.getData().getReportIds(), filterDto
    );
    return transformToCombinedReportResult(combinedReportDefinitionDto, resultList);
  }

  private CombinedProcessReportResult transformToCombinedReportResult(
    final CombinedReportDefinitionDto combinedReportDefinition,
    final List<ReportEvaluationResult> singleReportResultList) {

    final AtomicReference<Class> singleReportResultType = new AtomicReference<>();
    final Map<String, ReportEvaluationResult> reportIdToMapResult = singleReportResultList
      .stream()
      .filter(this::isProcessMapOrNumberResult)
      .filter(singleReportResult -> singleReportResult.getResultAsDto().getClass().equals(singleReportResultType.get())
        || singleReportResultType.compareAndSet(null, singleReportResult.getResultAsDto().getClass()))
      .collect(Collectors.toMap(
        ReportEvaluationResult::getId,
        singleReportResultDto -> singleReportResultDto,
        (u, v) -> {
          throw new IllegalStateException(String.format("Duplicate key %s", u));
        },
        LinkedHashMap::new
      ));
    final CombinedProcessReportResultDto combinedSingleReportResultDto =
      new CombinedProcessReportResultDto(reportIdToMapResult);
    return new CombinedProcessReportResult(combinedSingleReportResultDto, combinedReportDefinition);
  }

  private boolean isProcessMapOrNumberResult(ReportEvaluationResult reportResult) {
    final ResultType resultType = reportResult.getResultAsDto().getType();
    return ResultType.MAP.equals(resultType) ||
      ResultType.NUMBER.equals(resultType);
  }

  private List<ReportEvaluationResult> evaluateListOfReportIds(final String userId,
                                                               final List<String> singleReportIds,
                                                               final AdditionalProcessReportEvaluationFilterDto filterDto) {
    List<SingleProcessReportDefinitionDto> singleReportDefinitions =
      reportReader.getAllSingleProcessReportsForIdsOmitXml(singleReportIds)
        .stream()
        .filter(reportDefinition -> getAuthorizedRole(userId, reportDefinition).isPresent())
        .peek(reportDefinition -> addAdditionalFilters(userId, reportDefinition, filterDto))
        .collect(Collectors.toList());
    return combinedReportEvaluator.evaluate(singleReportDefinitions);
  }

  private void addAdditionalFilters(final String userId,
                                    final ReportDefinitionDto<?> reportDefinitionDto,
                                    final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
    if (additionalFilters != null && !CollectionUtils.isEmpty(additionalFilters.getFilter())) {
      if (reportDefinitionDto instanceof SingleProcessReportDefinitionDto) {
        SingleProcessReportDefinitionDto definitionDto = (SingleProcessReportDefinitionDto) reportDefinitionDto;
        Map<VariableType, Set<String>> variableFiltersByTypeForReport =
          processVariableService.getVariableNamesForReports(
            userId,
            Collections.singletonList(definitionDto.getId())
          )
            .stream()
            .collect(Collectors.groupingBy(
              ProcessVariableNameResponseDto::getType,
              mapping(ProcessVariableNameResponseDto::getName, Collectors.toSet())
            ));

        final List<ProcessFilterDto<?>> additionalFiltersToApply = additionalFilters.getFilter().stream()
          .filter(additionalFilter -> {
            if (additionalFilter.getData() instanceof VariableFilterDataDto) {
              final VariableFilterDataDto<?> filterData = (VariableFilterDataDto<?>) additionalFilter.getData();
              final Set<String> variableNamesForType =
                variableFiltersByTypeForReport.getOrDefault(filterData.getType(), Collections.emptySet());
              return variableNamesForType.contains(filterData.getName());
            }
            return true;
          })
          .collect(Collectors.toList());

        final List<ProcessFilterDto<?>> existingFilter = definitionDto.getData().getFilter();
        if (existingFilter != null) {
          existingFilter.addAll(additionalFiltersToApply);
        } else {
          definitionDto.getData().setFilter(additionalFiltersToApply);
        }
      } else {
        logger.debug(
          "Cannot add additional filters to report [{}] as it is not a process report",
          reportDefinitionDto.getId()
        );
      }
    }
  }

  /**
   * Checks if the user is allowed to see the given report.
   */
  protected abstract Optional<RoleType> getAuthorizedRole(String userId, ReportDefinitionDto report);

  private ReportEvaluationResult evaluateSingleReportWithErrorCheck(final String userId,
                                                                    final AuthorizedReportDefinitionDto reportDefinition,
                                                                    final Integer customRecordLimit,
                                                                    final AdditionalProcessReportEvaluationFilterDto filterDto) {
    addAdditionalFilters(userId, reportDefinition.getDefinitionDto(), filterDto);
    try {
      CommandContext<ReportDefinitionDto> context = new CommandContext<>();
      context.setReportDefinition(reportDefinition.getDefinitionDto());
      context.setRecordLimit(customRecordLimit);
      return singleReportEvaluator.evaluate(context);
    } catch (OptimizeException | OptimizeValidationException e) {
      throw new ReportEvaluationException(reportDefinition, e);
    }
  }

}
