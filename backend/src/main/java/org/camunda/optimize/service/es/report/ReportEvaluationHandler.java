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
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.process.CombinedProcessReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.exceptions.evaluation.TooManyBucketsException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.variable.ProcessVariableService;
import org.elasticsearch.ElasticsearchStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;

@RequiredArgsConstructor
@Component
public abstract class ReportEvaluationHandler {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected final ReportReader reportReader;
  private final SingleReportEvaluator singleReportEvaluator;
  private final CombinedReportEvaluator combinedReportEvaluator;
  private final ProcessVariableService processVariableService;

  public AuthorizedReportEvaluationResult evaluateReport(final ReportEvaluationInfo evaluationInfo) {
    evaluationInfo.postFetchSavedReport(reportReader);
    final RoleType currentUserRole = getAuthorizedRole(evaluationInfo.getUserId(), evaluationInfo.getReport())
      .orElseThrow(() -> new ForbiddenException(String.format(
        "User [%s] is not authorized to evaluate report [%s].",
        evaluationInfo.getUserId(),
        evaluationInfo.getReport().getName()
      )));
    final ReportEvaluationResult<?, ?> result;
    if (evaluationInfo.getReport().isCombined()) {
      result = evaluateCombinedReport(evaluationInfo, currentUserRole);
    } else {
      result = evaluateSingleReportWithErrorCheck(evaluationInfo, currentUserRole);
    }
    return new AuthorizedReportEvaluationResult(result, currentUserRole);
  }

  private CombinedProcessReportResult evaluateCombinedReport(final ReportEvaluationInfo evaluationInfo,
                                                             final RoleType currentUserRole) {
    final CombinedReportDefinitionRequestDto combinedReportDefinitionDto =
      (CombinedReportDefinitionRequestDto) evaluationInfo.getReport();
    ValidationHelper.validateCombinedReportDefinition(combinedReportDefinitionDto, currentUserRole);
    Optional.ofNullable(evaluationInfo.getPagination())
      .ifPresent(pagination -> {
        if (pagination.getLimit() != null || pagination.getOffset() != null) {
          throw new OptimizeValidationException("Pagination cannot be applied to combined reports");
        }
      });
    final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions =
      getAuthorizedSingleReportDefinitions(evaluationInfo);
    final List<ReportEvaluationResult> resultList =
      combinedReportEvaluator.evaluate(singleReportDefinitions, evaluationInfo.getTimezone());
    final long instanceCount = combinedReportEvaluator.evaluateCombinedReportInstanceCount(singleReportDefinitions);
    return transformToCombinedReportResult(combinedReportDefinitionDto, resultList, instanceCount);
  }

  private CombinedProcessReportResult transformToCombinedReportResult(
    final CombinedReportDefinitionRequestDto combinedReportDefinition,
    final List<ReportEvaluationResult> singleReportResultList,
    final long instanceCount) {

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
      new CombinedProcessReportResultDto(reportIdToMapResult, instanceCount);
    return new CombinedProcessReportResult(combinedSingleReportResultDto, combinedReportDefinition);
  }

  private boolean isProcessMapOrNumberResult(ReportEvaluationResult reportResult) {
    final ResultType resultType = reportResult.getResultAsDto().getType();
    return ResultType.MAP.equals(resultType) ||
      ResultType.NUMBER.equals(resultType);
  }

  private List<SingleProcessReportDefinitionRequestDto> getAuthorizedSingleReportDefinitions(final ReportEvaluationInfo evaluationInfo) {
    final CombinedReportDefinitionRequestDto combinedReportDefinitionDto =
      (CombinedReportDefinitionRequestDto) evaluationInfo.getReport();
    final String userId = evaluationInfo.getUserId();
    List<String> singleReportIds = combinedReportDefinitionDto.getData().getReportIds();
    List<SingleProcessReportDefinitionRequestDto> foundSingleReports =
      reportReader.getAllSingleProcessReportsForIdsOmitXml(
        singleReportIds)
        .stream()
        .filter(reportDefinition -> getAuthorizedRole(userId, reportDefinition).isPresent())
        .peek(reportDefinition -> addAdditionalFiltersForAuthorizedReport(
          userId,
          reportDefinition,
          evaluationInfo.getAdditionalFilters()
        ))
        .collect(Collectors.toList());

    if (foundSingleReports.size() != singleReportIds.size()) {
      throw new OptimizeValidationException("Some of the single reports contained in the combined report with id ["
                                              + combinedReportDefinitionDto.getId() + "] could not be found");
    }
    return foundSingleReports;
  }

  /**
   * Checks if the user is allowed to see the given report.
   */
  protected abstract Optional<RoleType> getAuthorizedRole(final String userId,
                                                          final ReportDefinitionDto report);

  private ReportEvaluationResult evaluateSingleReportWithErrorCheck(final ReportEvaluationInfo evaluationInfo,
                                                                    final RoleType currentUserRole) {
    if (evaluationInfo.isSharedReport()) {
      addAdditionalFiltersForReport(evaluationInfo.getReport(), evaluationInfo.getAdditionalFilters());
    } else {
      addAdditionalFiltersForAuthorizedReport(
        evaluationInfo.getUserId(),
        evaluationInfo.getReport(),
        evaluationInfo.getAdditionalFilters()
      );
    }
    try {
      CommandContext<ReportDefinitionDto<?>> context = CommandContext.fromReportEvaluation(evaluationInfo);
      return singleReportEvaluator.evaluate(context);
    } catch (OptimizeException | OptimizeValidationException e) {
      final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto =
        new AuthorizedReportDefinitionResponseDto(evaluationInfo.getReport(), currentUserRole);
      throw new ReportEvaluationException(authorizedReportDefinitionDto, e);
    } catch (ElasticsearchStatusException e) {
      final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto =
        new AuthorizedReportDefinitionResponseDto(evaluationInfo.getReport(), currentUserRole);
      if (Arrays.stream(e.getSuppressed())
        .map(Throwable::getMessage)
        .anyMatch(msg -> msg.contains("too_many_buckets_exception"))) {
        throw new TooManyBucketsException(authorizedReportDefinitionDto, e);
      }
      throw e;
    }
  }

  private void addAdditionalFiltersForAuthorizedReport(final String userId,
                                                       final ReportDefinitionDto<?> reportDefinitionDto,
                                                       final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
    addAdditionalFiltersForReport(
      reportDefinitionDto,
      additionalFilters,
      () -> processVariableService.getVariableNamesForAuthorizedReports(
        userId,
        Collections.singletonList(reportDefinitionDto.getId())
      )
    );
  }

  private void addAdditionalFiltersForReport(final ReportDefinitionDto<?> reportDefinitionDto,
                                             final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
    addAdditionalFiltersForReport(
      reportDefinitionDto,
      additionalFilters,
      () -> processVariableService.getVariableNamesForReports(Collections.singletonList(reportDefinitionDto.getId()))
    );
  }

  private void addAdditionalFiltersForReport(final ReportDefinitionDto<?> reportDefinitionDto,
                                             final AdditionalProcessReportEvaluationFilterDto additionalFilters,
                                             Supplier<List<ProcessVariableNameResponseDto>> varNameSupplier) {
    if (additionalFilters != null && !CollectionUtils.isEmpty(additionalFilters.getFilter())) {
      if (reportDefinitionDto instanceof SingleProcessReportDefinitionRequestDto) {
        SingleProcessReportDefinitionRequestDto definitionDto =
          (SingleProcessReportDefinitionRequestDto) reportDefinitionDto;
        Map<VariableType, Set<String>> variableFiltersByTypeForReport =
          varNameSupplier.get()
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

}
