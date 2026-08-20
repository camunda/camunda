/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.groupby.process.date;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface ProcessGroupByProcessInstanceRunningDateInterpreter {
  static Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Supplier<MinMaxStatDto> minMaxDateRangeForCrossFieldSupplier) {
    if (context.getReportData().getGroupBy().getValue()
        instanceof final DateGroupByValueDto groupByDate) {
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(minMaxDateRangeForCrossFieldSupplier.get());
      }
    }
    return Optional.empty();
  }

  static void addQueryResult(
      final CompositeCommandResult result,
      final List<GroupByResult> groups,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    result.setGroups(groups);
    result.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC)));
  }

  static AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }
}
