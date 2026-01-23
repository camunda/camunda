/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static io.camunda.optimize.service.util.ExceptionUtil.isTooManyBucketsException;
import static java.util.stream.Collectors.mapping;

import io.camunda.optimize.OptimizeMetrics;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.CombinedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.exceptions.OptimizeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import io.camunda.optimize.service.exceptions.evaluation.TooManyBucketsException;
import io.camunda.optimize.service.identity.CollapsedSubprocessNodesService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.ValidationHelper;
import io.camunda.optimize.service.variable.ProcessVariableService;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public abstract class ReportEvaluationHandler {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final ReportService reportService;
  private final SingleReportEvaluator singleReportEvaluator;
  private final CombinedReportEvaluator combinedReportEvaluator;
  private final ProcessVariableService processVariableService;
  private final DefinitionService definitionService;
  private final CollapsedSubprocessNodesService collapsedSubprocessNodesService;

  public ReportEvaluationHandler(
      final ReportService reportService,
      final SingleReportEvaluator singleReportEvaluator,
      final CombinedReportEvaluator combinedReportEvaluator,
      final ProcessVariableService processVariableService,
      final DefinitionService definitionService,
      final CollapsedSubprocessNodesService collapsedSubprocessNodesService) {
    this.reportService = reportService;
    this.singleReportEvaluator = singleReportEvaluator;
    this.combinedReportEvaluator = combinedReportEvaluator;
    this.processVariableService = processVariableService;
    this.definitionService = definitionService;
    this.collapsedSubprocessNodesService = collapsedSubprocessNodesService;
  }

  public AuthorizedReportEvaluationResult evaluateReport(
      final ReportEvaluationInfo evaluationInfo) {
    evaluationInfo.postFetchSavedReport(reportService);
    updateAndSetLatestReportDefinitionXml(evaluationInfo);
    setDataSourcesForSystemGeneratedReports(evaluationInfo);
    final RoleType currentUserRole =
        getAuthorizedRole(evaluationInfo.getUserId(), evaluationInfo.getReport())
            .orElseThrow(
                () ->
                    new ForbiddenException(
                        String.format(
                            "User [%s] is not authorized to evaluate report [%s].",
                            evaluationInfo.getUserId(), evaluationInfo.getReport().getName())));

    // Start timer for report evaluation metrics
    final String reportType = evaluationInfo.getReport().isCombined() ? "combined" : "single";
    final Timer.Sample timerSample = Timer.start();

    final ReportEvaluationResult result;
    try {
      if (evaluationInfo.getReport().isCombined()) {
        result = evaluateCombinedReport(evaluationInfo, currentUserRole);
      } else {
        result = evaluateSingleReportWithErrorCheck(evaluationInfo, currentUserRole);
      }
    } finally {
      // Record the time taken to evaluate the report
      timerSample.stop(
          OptimizeMetrics.getReportEvaluationTimer(
              reportType,
              Optional.ofNullable(evaluationInfo.getReport().getName()).orElse("unknown")));
    }

    return new AuthorizedReportEvaluationResult(result, currentUserRole);
  }

  private void updateAndSetLatestReportDefinitionXml(
      final ReportEvaluationInfo reportEvaluationInfo) {
    reportService
        .updateReportDefinitionXmlIfRequiredAndReturn(reportEvaluationInfo.getReport())
        .ifPresent(reportEvaluationInfo::updateReportDefinitionXml);
  }

  private void setDataSourcesForSystemGeneratedReports(
      final ReportEvaluationInfo reportEvaluationInfo) {
    // Overwrite tenant selection for management and instant reports to ensure reports are always
    // evaluating data
    // from all tenants the given definition currently exists on
    if (reportEvaluationInfo.getReport().getData()
        instanceof final ProcessReportDataDto processReportData) {
      if (processReportData.isManagementReport()) {
        final List<ReportDataDefinitionDto> definitionsForManagementReport =
            definitionService
                .getFullyImportedDefinitions(
                    DefinitionType.PROCESS, reportEvaluationInfo.getUserId())
                .stream()
                .map(
                    def ->
                        new ReportDataDefinitionDto(
                            def.getKey(),
                            def.getName(),
                            List.of(ALL_VERSIONS),
                            def.getTenants().stream()
                                .map(TenantDto::getId)
                                .collect(Collectors.toList()),
                            def.getName()))
                .collect(Collectors.toList());
        processReportData.setDefinitions(definitionsForManagementReport);
      } else if (processReportData.isInstantPreviewReport()
          && !reportEvaluationInfo.isSharedReport()) {
        // The same logic as above, but just for the single process definition in the report
        final String key =
            ((SingleReportDataDto) reportEvaluationInfo.getReport().getData()).getDefinitionKey();
        final List<ReportDataDefinitionDto> definitionForInstantPreviewReport =
            definitionService
                .getDefinitionWithAvailableTenants(
                    DefinitionType.PROCESS, key, reportEvaluationInfo.getUserId())
                .stream()
                .map(
                    def ->
                        new ReportDataDefinitionDto(
                            def.getKey(),
                            def.getName(),
                            List.of(ALL_VERSIONS),
                            def.getTenants().stream()
                                .map(TenantDto::getId)
                                .collect(Collectors.toList()),
                            def.getName()))
                .collect(Collectors.toList());

        processReportData.setDefinitions(definitionForInstantPreviewReport);
      }
    }
  }

  private CombinedReportEvaluationResult evaluateCombinedReport(
      final ReportEvaluationInfo evaluationInfo, final RoleType currentUserRole) {
    final CombinedReportDefinitionRequestDto combinedReportDefinitionDto =
        (CombinedReportDefinitionRequestDto) evaluationInfo.getReport();
    ValidationHelper.validateCombinedReportDefinition(combinedReportDefinitionDto, currentUserRole);
    evaluationInfo
        .getPagination()
        .ifPresent(
            pagination -> {
              if (pagination.getLimit() != null || pagination.getOffset() != null) {
                throw new OptimizeValidationException(
                    "Pagination cannot be applied to combined reports");
              }
            });
    final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions =
        getAuthorizedSingleReportDefinitions(evaluationInfo);
    final AtomicReference<Class<?>> singleReportResultType = new AtomicReference<>();
    final List<SingleReportEvaluationResult<?>> resultList =
        combinedReportEvaluator
            .evaluate(singleReportDefinitions, evaluationInfo.getTimezone())
            .stream()
            .filter(this::isProcessMapOrNumberResult)
            .filter(
                singleReportResult ->
                    singleReportResult
                            .getFirstCommandResult()
                            .getClass()
                            .equals(singleReportResultType.get())
                        || singleReportResultType.compareAndSet(
                            null, singleReportResult.getFirstCommandResult().getClass()))
            .collect(Collectors.toList());
    final long instanceCount =
        combinedReportEvaluator.evaluateCombinedReportInstanceCount(singleReportDefinitions);
    return new CombinedReportEvaluationResult(
        resultList, instanceCount, combinedReportDefinitionDto);
  }

  private boolean isProcessMapOrNumberResult(final SingleReportEvaluationResult<?> reportResult) {
    final ResultType resultType = reportResult.getFirstCommandResult().getType();
    return ResultType.MAP.equals(resultType) || ResultType.NUMBER.equals(resultType);
  }

  private List<SingleProcessReportDefinitionRequestDto> getAuthorizedSingleReportDefinitions(
      final ReportEvaluationInfo evaluationInfo) {
    final CombinedReportDefinitionRequestDto combinedReportDefinitionDto =
        (CombinedReportDefinitionRequestDto) evaluationInfo.getReport();
    final String userId = evaluationInfo.getUserId();
    final List<String> singleReportIds = combinedReportDefinitionDto.getData().getReportIds();
    final List<SingleProcessReportDefinitionRequestDto> foundSingleReports =
        reportService.getAllSingleProcessReportsForIdsOmitXml(singleReportIds).stream()
            .filter(reportDefinition -> getAuthorizedRole(userId, reportDefinition).isPresent())
            .peek(
                reportDefinition -> addAdditionalFiltersForReport(evaluationInfo, reportDefinition))
            .collect(Collectors.toList());

    if (foundSingleReports.size() != singleReportIds.size()) {
      throw new OptimizeValidationException(
          "Some of the single reports contained in the combined report with id ["
              + combinedReportDefinitionDto.getId()
              + "] could not be found");
    }
    return foundSingleReports;
  }

  /** Checks if the user is allowed to see the given report. */
  protected abstract Optional<RoleType> getAuthorizedRole(
      final String userId, final ReportDefinitionDto<?> report);

  private ReportEvaluationResult evaluateSingleReportWithErrorCheck(
      final ReportEvaluationInfo evaluationInfo, final RoleType currentUserRole) {
    addAdditionalFiltersForReport(evaluationInfo, evaluationInfo.getReport());
    addHiddenFlowNodeIds(evaluationInfo);
    try {
      final ReportEvaluationContext<SingleReportDefinitionDto<SingleReportDataDto>> context =
          ReportEvaluationContext.fromReportEvaluation(evaluationInfo);
      return singleReportEvaluator.evaluate(context);
    } catch (final OptimizeException | OptimizeValidationException e) {
      final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto =
          new AuthorizedReportDefinitionResponseDto(evaluationInfo.getReport(), currentUserRole);
      throw new ReportEvaluationException(authorizedReportDefinitionDto, e);
    } catch (final RuntimeException e) {
      if (isTooManyBucketsException(e)) {
        final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto =
            new AuthorizedReportDefinitionResponseDto(evaluationInfo.getReport(), currentUserRole);
        throw new TooManyBucketsException(authorizedReportDefinitionDto, e);
      } else {
        throw e;
      }
    }
  }

  private void addAdditionalFiltersForReport(
      final ReportEvaluationInfo evaluationInfo, final ReportDefinitionDto<?> reportDefinition) {
    if (evaluationInfo.isSharedReport()) {
      addAdditionalFiltersForReport(reportDefinition, evaluationInfo.getAdditionalFilters());
    } else {
      addAdditionalFiltersForAuthorizedReport(
          evaluationInfo.getUserId(), reportDefinition, evaluationInfo.getAdditionalFilters());
    }
  }

  private void addHiddenFlowNodeIds(final ReportEvaluationInfo evaluationInfo) {
    if (evaluationInfo.getReport().getData()
            instanceof final ProcessReportDataDto processReportDataDto
        && processReportDataDto.getVisualization() == ProcessVisualization.HEAT
        && Optional.ofNullable(processReportDataDto.getConfiguration())
            .map(SingleReportConfigurationDto::getXml)
            .isPresent()) {
      evaluationInfo.setHiddenFlowNodeIds(
          collapsedSubprocessNodesService.getCollapsedSubprocessNodeIdsForReport(
              processReportDataDto));
    }
  }

  private void addAdditionalFiltersForAuthorizedReport(
      final String userId,
      final ReportDefinitionDto<?> reportDefinitionDto,
      final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
    addAdditionalFiltersForReport(
        reportDefinitionDto,
        additionalFilters,
        () ->
            processVariableService.getVariableNamesForAuthorizedReports(
                userId, Collections.singletonList(reportDefinitionDto.getId())));
  }

  private void addAdditionalFiltersForReport(
      final ReportDefinitionDto<?> reportDefinitionDto,
      final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
    addAdditionalFiltersForReport(
        reportDefinitionDto,
        additionalFilters,
        () ->
            processVariableService.getVariableNamesForReports(
                Collections.singletonList(reportDefinitionDto.getId())));
  }

  private void addAdditionalFiltersForReport(
      final ReportDefinitionDto<?> reportDefinitionDto,
      final AdditionalProcessReportEvaluationFilterDto additionalFilters,
      final Supplier<List<ProcessVariableNameResponseDto>> varNameSupplier) {
    if (additionalFilters != null && !CollectionUtils.isEmpty(additionalFilters.getFilter())) {
      if (reportDefinitionDto
          instanceof final SingleProcessReportDefinitionRequestDto definitionDto) {
        final EnumMap<VariableType, Set<String>> variableFiltersByTypeForReport;
        // We only fetch the variable filter values if a variable filter is present
        if (additionalFilters.getFilter().stream()
            .anyMatch(filter -> filter.getData() instanceof VariableFilterDataDto)) {
          variableFiltersByTypeForReport =
              varNameSupplier.get().stream()
                  .collect(
                      Collectors.groupingBy(
                          ProcessVariableNameResponseDto::getType,
                          () -> new EnumMap<>(VariableType.class),
                          mapping(ProcessVariableNameResponseDto::getName, Collectors.toSet())));
        } else {
          variableFiltersByTypeForReport = new EnumMap<>(VariableType.class);
        }

        final List<ProcessFilterDto<?>> additionalFiltersToApply =
            additionalFilters.getFilter().stream()
                .filter(
                    additionalFilter -> {
                      if (additionalFilter.getData()
                          instanceof final VariableFilterDataDto<?> filterData) {
                        final Set<String> variableNamesForType =
                            variableFiltersByTypeForReport.getOrDefault(
                                filterData.getType(), Collections.emptySet());
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
            reportDefinitionDto.getId());
      }
    }
  }
}
