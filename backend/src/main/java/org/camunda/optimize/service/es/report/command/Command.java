/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.search.aggregations.metrics.Stats;

import java.util.Optional;

public interface Command<RD extends ReportDefinitionDto<?>> {

  ReportEvaluationResult<?, RD> evaluate(CommandContext<RD> commandContext) throws OptimizeException;

  String createCommandKey();

  default Optional<Stats> calculateDateRangeForAutomaticGroupByDate(
    final CommandContext<SingleProcessReportDefinitionDto> reportDefinitionDto) {
    // this method is used for *combined* grouped by date with automatic interval
    // commands to calculate what's the total data range. This allows to calculate
    // the same date interval for each single report in the combined report.
    // By default it's assumed that there is no date range to be calculated.
    return Optional.empty();
  }

  default Optional<Stats> calculateNumberRangeForCombinedGroupByNumberVariable(
    final CommandContext<SingleProcessReportDefinitionDto> reportDefinitionDto) {
    // this method is used for *combined* grouped by number variable report
    // to calculate what's the total data range. This allows to calculate
    // the same number interval for each single report in the combined report.
    // By default, it's assumed that there is no number range to be calculated.
    return Optional.empty();
  }
}
