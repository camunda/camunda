/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.date;

import static io.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_INSTANCE_RUNNING_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.interpreter.groupby.process.date.ProcessGroupByProcessInstanceRunningDateInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByProcessInstanceRunningDateInterpreterES
    extends AbstractProcessGroupByInterpreterES {

  private final DateTimeFormatter formatter;
  private final DateAggregationServiceES dateAggregationService;
  private final MinMaxStatsServiceES minMaxStatsService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessGroupByProcessInstanceRunningDateInterpreterES(
      final DateTimeFormatter formatter,
      final DateAggregationServiceES dateAggregationService,
      final MinMaxStatsServiceES minMaxStatsService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
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
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxDateRangeForCrossField(
            context,
            Query.of(q -> q.bool(boolQuery)),
            getIndexNames(context),
            START_DATE,
            END_DATE);

    final DateAggregationContextES dateAggContext =
        DateAggregationContextES.builder()
            .aggregateByDateUnit(
                ProcessGroupByProcessInstanceRunningDateInterpreter.getGroupByDateUnit(
                    context.getReportData()))
            .minMaxStats(minMaxStats)
            .dateField(ProcessInstanceDto.Fields.startDate)
            .runningDateReportEndDateField(ProcessInstanceDto.Fields.endDate)
            .timezone(context.getTimezone())
            .subAggregations(getDistributedByInterpreter().createAggregations(context, boolQuery))
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService.createRunningDateAggregation(dateAggContext).orElse(Map.of());
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult result,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (response.aggregations() != null && !response.aggregations().isEmpty()) {
      // Only enrich result if aggregations exist (if no aggregations exist, this report contains no
      // instances)
      ProcessGroupByProcessInstanceRunningDateInterpreter.addQueryResult(
          result, processAggregations(response, response.aggregations(), context), context);
    }
  }

  private List<CompositeCommandResult.GroupByResult> processAggregations(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final FiltersAggregate agg = aggregations.get(FILTER_LIMITED_AGGREGATION).filters();

    final List<CompositeCommandResult.GroupByResult> results = new ArrayList<>();

    for (final Map.Entry<String, FiltersBucket> entry : agg.buckets().keyed().entrySet()) {
      final String key = formatToCorrectTimezone(entry.getKey(), context.getTimezone(), formatter);
      final List<CompositeCommandResult.DistributedByResult> distributions =
          getDistributedByInterpreter()
              .retrieveResult(response, entry.getValue().aggregations(), context);
      results.add(CompositeCommandResult.GroupByResult.createGroupByResult(key, distributions));
    }
    return results;
  }

  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
