/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_DURATION;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.util.DurationScriptUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DurationAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByDurationInterpreterOS extends AbstractProcessGroupByInterpreterOS {

  private final DurationAggregationServiceOS durationAggregationService;
  private final MinMaxStatsServiceOS minMaxStatsService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByDurationInterpreterOS(
      final DurationAggregationServiceOS durationAggregationService,
      final MinMaxStatsServiceOS minMaxStatsService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.durationAggregationService = durationAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_DURATION);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query baseQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Script durationScript = getDurationScript();
    return durationAggregationService
        .createLimitedGroupByScriptedDurationAggregation(baseQuery, context, durationScript)
        .stream()
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<GroupByResult> durationHistogramData =
        durationAggregationService.mapGroupByDurationResults(
            response, response.aggregations(), context);

    compositeCommandResult.setGroups(durationHistogramData);
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByInterpreter.isKeyOfNumericType(context));
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(context, baseQuery));
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return minMaxStatsService.getScriptedMinMaxStats(
        baseQuery, getIndexNames(context), null, getDurationScript());
  }

  private Script getDurationScript() {
    return DurationScriptUtilOS.getDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        ProcessInstanceIndex.DURATION,
        ProcessInstanceIndex.START_DATE);
  }

  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }
}
