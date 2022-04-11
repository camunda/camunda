/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.RunningDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filters;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByProcessInstanceRunningDate extends ProcessGroupByPart {
  private final DateTimeFormatter formatter;
  private final DateAggregationService dateAggregationService;
  private final MinMaxStatsService minMaxStatsService;

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context,
                                                final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      DateGroupByValueDto groupByDate = (DateGroupByValueDto) context.getReportData().getGroupBy().getValue();
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(
          minMaxStatsService.getMinMaxDateRangeForCrossField(
            context,
            baseQuery,
            getIndexNames(context),
            START_DATE,
            END_DATE
          ));
      }
    }
    return Optional.empty();
  }

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final MinMaxStatDto minMaxStats = minMaxStatsService.getMinMaxDateRangeForCrossField(
      context,
      searchSourceBuilder.query(),
      getIndexNames(context),
      START_DATE,
      END_DATE
    );

    final DateAggregationContext dateAggContext = DateAggregationContext.builder()
      .aggregateByDateUnit(getGroupByDateUnit(context.getReportData()))
      .minMaxStats(minMaxStats)
      .dateField(ProcessInstanceDto.Fields.startDate)
      .runningDateReportEndDateField(ProcessInstanceDto.Fields.endDate)
      .timezone(context.getTimezone())
      .subAggregations(distributedByPart.createAggregations(context))
      .filterContext(context.getFilterContext())
      .build();

    return dateAggregationService.createRunningDateAggregation(dateAggContext)
      .map(Collections::singletonList)
      .orElse(Collections.emptyList());
  }

  @Override
  protected void addQueryResult(final CompositeCommandResult result,
                                final SearchResponse response,
                                final ExecutionContext<ProcessReportDataDto> context) {
    if (response.getAggregations() != null) {
      // Only enrich result if aggregations exist (if no aggregations exist, this report contains no instances)
      result.setGroups(processAggregations(response, response.getAggregations(), context));
      result.setGroupBySorting(
        context.getReportConfiguration()
          .getSorting()
          .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC))
      );
    }
  }

  private List<CompositeCommandResult.GroupByResult> processAggregations(
    final SearchResponse response,
    final Aggregations aggregations,
    final ExecutionContext<ProcessReportDataDto> context) {
    Filters agg = aggregations.get(FILTER_LIMITED_AGGREGATION);

    List<CompositeCommandResult.GroupByResult> results = new ArrayList<>();

    for (Filters.Bucket entry : agg.getBuckets()) {
      String key = formatToCorrectTimezone(entry.getKeyAsString(), context.getTimezone(), formatter);
      final List<CompositeCommandResult.DistributedByResult> distributions =
        distributedByPart.retrieveResult(response, entry.getAggregations(), context);
      results.add(
        CompositeCommandResult.GroupByResult.createGroupByResult(key, distributions)
      );
    }
    return results;
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setGroupBy(new RunningDateGroupByDto());
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }
}
