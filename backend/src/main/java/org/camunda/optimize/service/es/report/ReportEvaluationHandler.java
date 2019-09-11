/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportNumberResultDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.result.process.CombinedProcessReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public abstract class ReportEvaluationHandler {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final ReportReader reportReader;
  private final SingleReportEvaluator singleReportEvaluator;
  private final CombinedReportEvaluator combinedReportEvaluator;

  public ReportEvaluationResult evaluateSavedReport(final String userId,
                                                    final String reportId) {
    return evaluateSavedReport(userId, reportId, null);
  }

  public ReportEvaluationResult evaluateSavedReport(final String userId,
                                                    final String reportId,
                                                    final Integer customRecordLimit) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    return evaluateReport(userId, reportDefinition, customRecordLimit);
  }

  public ReportEvaluationResult evaluateReport(final String userId,
                                               final ReportDefinitionDto reportDefinition) {
    return evaluateReport(userId, reportDefinition, null);
  }

  public ReportEvaluationResult evaluateReport(final String userId,
                                               final ReportDefinitionDto reportDefinition,
                                               final Integer customRecordLimit) {
    if (!isAuthorizedToAccessReport(userId, reportDefinition)) {
      throw new ForbiddenException(
        "User [" + userId + "] is not authorized to evaluate report [" + reportDefinition.getName() + "]."
      );
    }

    final ReportEvaluationResult result;
    if (!reportDefinition.getCombined()) {
      result = evaluateSingleReportWithErrorCheck(reportDefinition, customRecordLimit);
    } else {
      result = evaluateCombinedReport(userId, (CombinedReportDefinitionDto) reportDefinition);
    }
    return result;
  }

  private CombinedProcessReportResult evaluateCombinedReport(String userId,
                                                             CombinedReportDefinitionDto combinedReportDefinition) {

    ValidationHelper.validateCombinedReportDefinition(combinedReportDefinition);
    List<ReportEvaluationResult> resultList = evaluateListOfReportIds(
      userId, combinedReportDefinition.getData().getReportIds()
    );
    return transformToCombinedReportResult(combinedReportDefinition, resultList);
  }

  private CombinedProcessReportResult transformToCombinedReportResult(
    CombinedReportDefinitionDto combinedReportDefinition,
    List<ReportEvaluationResult> singleReportResultList) {

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
    final CombinedProcessReportResultDto combinedProcessReportResultDto = new CombinedProcessReportResultDto(
      reportIdToMapResult
    );
    return new CombinedProcessReportResult(combinedProcessReportResultDto, combinedReportDefinition);
  }

  private boolean isProcessMapOrNumberResult(ReportEvaluationResult reportResult) {
    final ReportResultDto resultAsDto = reportResult.getResultAsDto();
    return resultAsDto instanceof ProcessReportNumberResultDto ||
      resultAsDto instanceof ProcessCountReportMapResultDto ||
      resultAsDto instanceof ProcessDurationReportNumberResultDto ||
      resultAsDto instanceof ProcessDurationReportMapResultDto;
  }

  private List<ReportEvaluationResult> evaluateListOfReportIds(final String userId,
                                                               final List<String> singleReportIds) {
    List<SingleProcessReportDefinitionDto> singleReportDefinitions =
      reportReader.getAllSingleProcessReportsForIdsOmitXml(singleReportIds)
        .stream()
        .filter(r -> isAuthorizedToAccessReport(userId, r))
        .collect(Collectors.toList());
    return combinedReportEvaluator.evaluate(singleReportDefinitions);
  }

  /**
   * Checks if the user is allowed to see the given report.
   */
  protected abstract boolean isAuthorizedToAccessReport(String userId, ReportDefinitionDto report);

  private ReportEvaluationResult evaluateSingleReportWithErrorCheck(final ReportDefinitionDto reportDefinition,
                                                                    final Integer customRecordLimit) {
    try {
      return singleReportEvaluator.evaluate(reportDefinition, customRecordLimit);
    } catch (OptimizeException | OptimizeValidationException e) {
      throw new ReportEvaluationException(reportDefinition, e);
    }
  }

}
