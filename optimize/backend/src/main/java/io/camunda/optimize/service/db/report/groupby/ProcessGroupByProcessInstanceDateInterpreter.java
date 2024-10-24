/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.groupby;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import java.util.List;

public interface ProcessGroupByProcessInstanceDateInterpreter {
  static void addQueryResult(
      final List<GroupByResult> groups,
      final boolean isDistributedByKeyOfNumericType,
      final CompositeCommandResult result,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    result.setGroups(groups);
    result.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC)));
    result.setGroupByKeyOfNumericType(false);
    result.setDistributedByKeyOfNumericType(isDistributedByKeyOfNumericType);
    final ProcessReportDataDto reportData = context.getReportData();
    // We sort by label for management report because keys change on every request
    if (reportData.isManagementReport()) {
      result.setDistributedBySorting(
          new ReportSortingDto(ReportSortingDto.SORT_BY_LABEL, SortOrder.ASC));
    }
  }
}
