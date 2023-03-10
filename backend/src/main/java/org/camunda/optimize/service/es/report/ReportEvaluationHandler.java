/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.CombinedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.reader.ReportReader;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TOO_MANY_BUCKETS_EXCEPTION_TYPE;

@RequiredArgsConstructor
@Component
public abstract class ReportEvaluationHandler {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected final ReportReader reportReader;
  private final SingleReportEvaluator singleReportEvaluator;
  private final CombinedReportEvaluator combinedReportEvaluator;
  private final ProcessVariableService processVariableService;
  private final DefinitionService definitionService;

  public AuthorizedReportEvaluationResult evaluateReport(final ReportEvaluationInfo evaluationInfo) {
    evaluationInfo.postFetchSavedReport(reportReader);
    setDataSourcesForSystemGeneratedReports(evaluationInfo);
    final RoleType currentUserRole = getAuthorizedRole(evaluationInfo.getUserId(), evaluationInfo.getReport())
      .orElseThrow(() -> new ForbiddenException(String.format(
        "User [%s] is not authorized to evaluate report [%s].",
        evaluationInfo.getUserId(),
        evaluationInfo.getReport().getName()
      )));
    final ReportEvaluationResult result;
    if (evaluationInfo.getReport().isCombined()) {
      result = evaluateCombinedReport(evaluationInfo, currentUserRole);
    } else {
      result = evaluateSingleReportWithErrorCheck(evaluationInfo, currentUserRole);
    }
    return new AuthorizedReportEvaluationResult(result, currentUserRole);
  }

  private void setDataSourcesForSystemGeneratedReports(final ReportEvaluationInfo reportEvaluationInfo) {
    // Overwrite tenant selection for management and instant reports to ensure reports are always evaluating data
    // from all tenants the given definition currently exists on
    if (reportEvaluationInfo.getReport().getData() instanceof ProcessReportDataDto) {
      final ProcessReportDataDto processReportData = (ProcessReportDataDto) reportEvaluationInfo.getReport()
        .getData();
      if (processReportData.isManagementReport()) {
        final List<ReportDataDefinitionDto> definitionsForManagementReport =
          definitionService.getFullyImportedDefinitions(DefinitionType.PROCESS, reportEvaluationInfo.getUserId())
            .stream()
            .map(def -> new ReportDataDefinitionDto(
              def.getKey(),
              def.getName(),
              List.of(ALL_VERSIONS),
              def.getTenants().stream().map(TenantDto::getId).collect(Collectors.toList()),
              def.getName()
            ))
            .collect(Collectors.toList());
        processReportData.setDefinitions(definitionsForManagementReport);
      }
      else if (processReportData.isInstantPreviewReport() && !reportEvaluationInfo.isSharedReport()) {
        // Same logic as above, but just for the single process definition in the report
        String key =
          ((SingleReportDataDto) reportEvaluationInfo.getReport().getData()).getDefinitionKey();
        List<ReportDataDefinitionDto> definitionForInstantPreviewReport =
          definitionService.getDefinitionWithAvailableTenants(DefinitionType.PROCESS,
                                                              key,
                                                              reportEvaluationInfo.getUserId())
            .stream()
            .map(def -> new ReportDataDefinitionDto(
              def.getKey(),
              def.getName(),
              List.of(ALL_VERSIONS),
              def.getTenants().stream().map(TenantDto::getId).collect(Collectors.toList()),
              def.getName()
            ))
            .collect(Collectors.toList());

        processReportData.setDefinitions(definitionForInstantPreviewReport);
      }
    }
  }

  private CombinedReportEvaluationResult evaluateCombinedReport(final ReportEvaluationInfo evaluationInfo,
                                                                final RoleType currentUserRole) {
    final CombinedReportDefinitionRequestDto combinedReportDefinitionDto =
      (CombinedReportDefinitionRequestDto) evaluationInfo.getReport();
    ValidationHelper.validateCombinedReportDefinition(combinedReportDefinitionDto, currentUserRole);
    evaluationInfo.getPagination()
      .ifPresent(pagination -> {
        if (pagination.getLimit() != null || pagination.getOffset() != null) {
          throw new OptimizeValidationException("Pagination cannot be applied to combined reports");
        }
      });
    final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions =
      getAuthorizedSingleReportDefinitions(evaluationInfo);
    final AtomicReference<Class<?>> singleReportResultType = new AtomicReference<>();
    final List<SingleReportEvaluationResult<?>> resultList =
      combinedReportEvaluator.evaluate(singleReportDefinitions, evaluationInfo.getTimezone())
        .stream()
        .filter(this::isProcessMapOrNumberResult)
        .filter(singleReportResult -> singleReportResult.getFirstCommandResult()
          .getClass()
          .equals(singleReportResultType.get())
          || singleReportResultType.compareAndSet(null, singleReportResult.getFirstCommandResult().getClass()))
        .collect(Collectors.toList());
    final long instanceCount = combinedReportEvaluator.evaluateCombinedReportInstanceCount(singleReportDefinitions);
    return new CombinedReportEvaluationResult(resultList, instanceCount, combinedReportDefinitionDto);
  }

  private boolean isProcessMapOrNumberResult(SingleReportEvaluationResult<?> reportResult) {
    final ResultType resultType = reportResult.getFirstCommandResult().getType();
    return ResultType.MAP.equals(resultType) ||
      ResultType.NUMBER.equals(resultType);
  }

  private List<SingleProcessReportDefinitionRequestDto> getAuthorizedSingleReportDefinitions(final ReportEvaluationInfo evaluationInfo) {
    final CombinedReportDefinitionRequestDto combinedReportDefinitionDto =
      (CombinedReportDefinitionRequestDto) evaluationInfo.getReport();
    final String userId = evaluationInfo.getUserId();
    List<String> singleReportIds = combinedReportDefinitionDto.getData().getReportIds();
    List<SingleProcessReportDefinitionRequestDto> foundSingleReports =
      reportReader.getAllSingleProcessReportsForIdsOmitXml(singleReportIds)
        .stream()
        .filter(reportDefinition -> getAuthorizedRole(userId, reportDefinition).isPresent())
        .peek(reportDefinition -> addAdditionalFiltersForReport(evaluationInfo, reportDefinition))
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
                                                          final ReportDefinitionDto<?> report);

  private ReportEvaluationResult evaluateSingleReportWithErrorCheck(final ReportEvaluationInfo evaluationInfo,
                                                                    final RoleType currentUserRole) {
    addAdditionalFiltersForReport(evaluationInfo, evaluationInfo.getReport());
    try {
      ReportEvaluationContext<ReportDefinitionDto<?>> context = ReportEvaluationContext
        .fromReportEvaluation(evaluationInfo);
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
        .anyMatch(msg -> msg.contains(TOO_MANY_BUCKETS_EXCEPTION_TYPE))) {
        throw new TooManyBucketsException(authorizedReportDefinitionDto, e);
      }
      throw e;
    }
  }

  private void addAdditionalFiltersForReport(final ReportEvaluationInfo evaluationInfo,
                                             final ReportDefinitionDto<?> reportDefinition) {
    if (evaluationInfo.isSharedReport()) {
      addAdditionalFiltersForReport(reportDefinition, evaluationInfo.getAdditionalFilters());
    } else {
      addAdditionalFiltersForAuthorizedReport(
        evaluationInfo.getUserId(), reportDefinition, evaluationInfo.getAdditionalFilters()
      );
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

        final EnumMap<VariableType, Set<String>> variableFiltersByTypeForReport;
        // We only fetch the variable filter values if a variable filter is present
        if (additionalFilters.getFilter().stream().anyMatch(filter -> filter.getData() instanceof VariableFilterDataDto)) {
          variableFiltersByTypeForReport =
            varNameSupplier.get()
              .stream()
              .collect(Collectors.groupingBy(
                ProcessVariableNameResponseDto::getType,
                () -> new EnumMap<>(VariableType.class),
                mapping(ProcessVariableNameResponseDto::getName, Collectors.toSet())
              ));
        } else {
          variableFiltersByTypeForReport = new EnumMap<>(VariableType.class);
        }

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
