/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static java.util.Objects.requireNonNullElseGet;

import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.report.result.MapCommandResult;
import io.camunda.optimize.service.db.report.result.NumberCommandResult;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.ZoneId;
import java.util.List;

/**
 * Evaluates saved agentic KPI reports through the {@link ReportEvaluationService}. Composed into
 * the agentic KPI tile integration tests instead of being inherited, so the tests only depend on
 * the evaluation behaviour they actually use.
 */
final class AgenticReportEvaluator {

  private static final String USER_ID = "testUser";
  private static final ZoneId UTC = ZoneId.of("UTC");

  private final ReportEvaluationService evaluationService;

  AgenticReportEvaluator(final ReportEvaluationService evaluationService) {
    this.evaluationService = evaluationService;
  }

  Double evaluateNumber(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final NumberCommandResult commandResult =
        (NumberCommandResult) firstCommandResult(reportId, filterDto);
    final List<MeasureDto<Double>> measures = commandResult.getMeasures();
    return measures.isEmpty() ? null : measures.getFirst().getData();
  }

  MapCommandResult evaluateMap(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    return (MapCommandResult) firstCommandResult(reportId, filterDto);
  }

  List<MapResultEntryDto> evaluateMapData(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    return evaluateMap(reportId, filterDto).getFirstMeasureData();
  }

  List<MapResultEntryDto> evaluateMapData(final String reportId) {
    return evaluateMapData(reportId, AgenticReportFilters.noExtraFilters());
  }

  List<MapResultEntryDto> evaluateMapMeasure(final String reportId, final int commandIndex) {
    final MapCommandResult commandResult =
        (MapCommandResult)
            evaluate(reportId, AgenticReportFilters.noExtraFilters(), null)
                .getCommandEvaluationResults()
                .get(commandIndex);
    return requireNonNullElseGet(commandResult.getFirstMeasureData(), List::of);
  }

  /**
   * Evaluates a report with an explicit {@link PaginationDto}, returning the raw command result so
   * tests can assert on pagination metadata or on the rejection of an invalid offset.
   */
  CommandEvaluationResult<?> evaluatePaginated(
      final String reportId,
      final AdditionalProcessReportEvaluationFilterDto filterDto,
      final PaginationDto pagination) {
    return evaluate(reportId, filterDto, pagination).getFirstCommandResult();
  }

  private CommandEvaluationResult<?> firstCommandResult(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    return evaluate(reportId, filterDto, null).getFirstCommandResult();
  }

  private SingleReportEvaluationResult<?> evaluate(
      final String reportId,
      final AdditionalProcessReportEvaluationFilterDto filterDto,
      final PaginationDto pagination) {
    return (SingleReportEvaluationResult<?>)
        evaluationService
            .evaluateSavedReportWithAdditionalFilters(USER_ID, UTC, reportId, filterDto, pagination)
            .getEvaluationResult();
  }
}
