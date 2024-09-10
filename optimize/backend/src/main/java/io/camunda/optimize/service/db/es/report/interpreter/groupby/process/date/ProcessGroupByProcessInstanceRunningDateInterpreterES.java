/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.date;

import static io.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtilES.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_INSTANCE_RUNNING_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filters;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByProcessInstanceRunningDateInterpreterES
    extends AbstractProcessGroupByInterpreterES {
  private final DateTimeFormatter formatter;
  private final DateAggregationServiceES dateAggregationService;
  private final MinMaxStatsServiceES minMaxStatsService;
  @Getter private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_PROCESS_INSTANCE_RUNNING_DATE);
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue()
        instanceof DateGroupByValueDto groupByDate) {
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(
            minMaxStatsService.getMinMaxDateRangeForCrossField(
                context, baseQuery, getIndexNames(context), START_DATE, END_DATE));
      }
    }
    return Optional.empty();
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxDateRangeForCrossField(
            context, searchSourceBuilder.query(), getIndexNames(context), START_DATE, END_DATE);

    final DateAggregationContextES dateAggContext =
        DateAggregationContextES.builder()
            .aggregateByDateUnit(getGroupByDateUnit(context.getReportData()))
            .minMaxStats(minMaxStats)
            .dateField(ProcessInstanceDto.Fields.startDate)
            .runningDateReportEndDateField(ProcessInstanceDto.Fields.endDate)
            .timezone(context.getTimezone())
            .subAggregations(
                getDistributedByInterpreter()
                    .createAggregations(context, searchSourceBuilder.query()))
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService
        .createRunningDateAggregation(dateAggContext)
        .map(Collections::singletonList)
        .orElse(Collections.emptyList());
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (response.getAggregations() != null) {
      // Only enrich result if aggregations exist (if no aggregations exist, this report contains no
      // instances)
      result.setGroups(processAggregations(response, response.getAggregations(), context));
      result.setGroupBySorting(
          context
              .getReportConfiguration()
              .getSorting()
              .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC)));
    }
  }

  private List<CompositeCommandResult.GroupByResult> processAggregations(
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    Filters agg = aggregations.get(FILTER_LIMITED_AGGREGATION);

    List<CompositeCommandResult.GroupByResult> results = new ArrayList<>();

    for (Filters.Bucket entry : agg.getBuckets()) {
      String key =
          formatToCorrectTimezone(entry.getKeyAsString(), context.getTimezone(), formatter);
      final List<CompositeCommandResult.DistributedByResult> distributions =
          getDistributedByInterpreter().retrieveResult(response, entry.getAggregations(), context);
      results.add(CompositeCommandResult.GroupByResult.createGroupByResult(key, distributions));
    }
    return results;
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }
}
