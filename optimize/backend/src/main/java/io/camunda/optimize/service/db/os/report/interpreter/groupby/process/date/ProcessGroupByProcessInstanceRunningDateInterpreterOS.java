/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.date;

import static io.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_INSTANCE_RUNNING_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.context.DateAggregationContextOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.interpreter.groupby.process.date.ProcessGroupByProcessInstanceRunningDateInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.MapUtil;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FiltersAggregate;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByProcessInstanceRunningDateInterpreterOS
    extends AbstractProcessGroupByInterpreterOS {

  private final DateTimeFormatter formatter;
  private final DateAggregationServiceOS dateAggregationService;
  private final MinMaxStatsServiceOS minMaxStatsService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByProcessInstanceRunningDateInterpreterOS(
      final DateTimeFormatter formatter,
      final DateAggregationServiceOS dateAggregationService,
      final MinMaxStatsServiceOS minMaxStatsService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.formatter = formatter;
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_PROCESS_INSTANCE_RUNNING_DATE);
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return ProcessGroupByProcessInstanceRunningDateInterpreter.getMinMaxStats(
        context,
        () ->
            minMaxStatsService.getMinMaxDateRangeForCrossField(
                context, baseQuery, getIndexNames(context), START_DATE, END_DATE));
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxDateRangeForCrossField(
            context, query, getIndexNames(context), START_DATE, END_DATE);

    final DateAggregationContextOS dateAggContext =
        DateAggregationContextOS.builder()
            .aggregateByDateUnit(
                ProcessGroupByProcessInstanceRunningDateInterpreter.getGroupByDateUnit(
                    context.getReportData()))
            .minMaxStats(minMaxStats)
            .dateField(ProcessInstanceDto.Fields.startDate)
            .runningDateReportEndDateField(ProcessInstanceDto.Fields.endDate)
            .timezone(context.getTimezone())
            .subAggregations(getDistributedByInterpreter().createAggregations(context, query))
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService
        .createRunningDateAggregation(dateAggContext)
        .map(MapUtil::createFromPair)
        .orElse(Map.of());
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (response.aggregations() != null) {
      // Only enrich result if aggregations exist (if no aggregations exist, this report contains no
      // instances)
      ProcessGroupByProcessInstanceRunningDateInterpreter.addQueryResult(
          result, processAggregations(response, response.aggregations(), context), context);
    }
  }

  private List<CompositeCommandResult.GroupByResult> processAggregations(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (!aggregations.containsKey(FILTER_LIMITED_AGGREGATION)) {
      return List.of();
    } else {
      final FiltersAggregate agg = aggregations.get(FILTER_LIMITED_AGGREGATION).filters();

      return agg.buckets().keyed().entrySet().stream()
          .map(
              entry -> {
                final String key =
                    formatToCorrectTimezone(entry.getKey(), context.getTimezone(), formatter);
                final List<CompositeCommandResult.DistributedByResult> distributions =
                    getDistributedByInterpreter()
                        .retrieveResult(response, entry.getValue().aggregations(), context);
                return CompositeCommandResult.GroupByResult.createGroupByResult(key, distributions);
              })
          .toList();
    }
  }

  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }
}
