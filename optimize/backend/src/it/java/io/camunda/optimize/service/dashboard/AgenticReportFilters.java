/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.util.List;

/** Pure builders for the additional filters applied when evaluating agentic KPI reports. */
final class AgenticReportFilters {

  private AgenticReportFilters() {}

  static AdditionalProcessReportEvaluationFilterDto noExtraFilters() {
    return new AdditionalProcessReportEvaluationFilterDto(List.of());
  }

  static AdditionalProcessReportEvaluationFilterDto rollingEndDateFilter(
      final long value, final DateUnit unit) {
    return new AdditionalProcessReportEvaluationFilterDto(
        List.of(rollingEndDateFilterDto(value, unit)));
  }

  static AdditionalProcessReportEvaluationFilterDto withDefinitions(
      final List<ReportDataDefinitionDto> definitions) {
    final AdditionalProcessReportEvaluationFilterDto dto =
        new AdditionalProcessReportEvaluationFilterDto(List.of());
    dto.setDefinitions(definitions);
    return dto;
  }

  static AdditionalProcessReportEvaluationFilterDto withDefinitionsAndDateFilter(
      final List<ReportDataDefinitionDto> definitions, final long value, final DateUnit unit) {
    final AdditionalProcessReportEvaluationFilterDto dto = withDefinitions(definitions);
    dto.setFilter(List.of(rollingEndDateFilterDto(value, unit)));
    return dto;
  }

  private static ProcessFilterDto<?> rollingEndDateFilterDto(
      final long value, final DateUnit unit) {
    final InstanceEndDateFilterDto filter = new InstanceEndDateFilterDto();
    filter.setData(new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit)));
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    return filter;
  }
}
