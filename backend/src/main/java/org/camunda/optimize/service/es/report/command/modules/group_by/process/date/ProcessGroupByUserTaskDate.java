/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.camunda.optimize.service.es.report.command.util.DateAggregationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.UserTaskFilterQueryUtil.createUserTaskAggregationFilter;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@Slf4j
@RequiredArgsConstructor
public abstract class ProcessGroupByUserTaskDate extends GroupByPart<ProcessReportDataDto> {

  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";

  private static final String FILTER_USER_TASKS_WITH_DATE_FIELD_SET_AGGREGATION =
    "filterUserTasksWithDateFieldSetAgg";

  private final DateAggregationService dateAggregationService;
  private final MinMaxStatsService minMaxStatsService;

  @Override
  public Optional<MinMaxStatDto> calculateDateRangeForAutomaticGroupByDate(final ExecutionContext<ProcessReportDataDto> context,
                                                                           final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      final GroupByDateUnit groupByDateUnit = getGroupByDateUnit(context.getReportData());
      if (GroupByDateUnit.AUTOMATIC.equals(groupByDateUnit)) {
        return Optional.of(
          minMaxStatsService.getMinMaxDateRangeForNestedField(
            context,
            baseQuery,
            PROCESS_INSTANCE_INDEX_NAME,
            getDateField(),
            USER_TASKS_AGGREGATION,
            createUserTaskAggregationFilter(context.getReportData())
          )
        );
      }
    }
    return Optional.empty();
  }

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final GroupByDateUnit unit = getGroupByDateUnit(context.getReportData());
    return createAggregation(searchSourceBuilder, context, unit);
  }

  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context,
                                                    final GroupByDateUnit unit) {
    final MinMaxStatDto stats = minMaxStatsService.getMinMaxDateRangeForNestedField(
      context,
      searchSourceBuilder.query(),
      PROCESS_INSTANCE_INDEX_NAME,
      getDateField(),
      USER_TASKS_AGGREGATION,
      createUserTaskAggregationFilter(context.getReportData())
    );

    final DateAggregationContext dateAggContext = DateAggregationContext.builder()
      .groupByDateUnit(unit)
      .dateField(getDateField())
      .minMaxStats(stats)
      .timezone(context.getTimezone())
      .distributedBySubAggregation(distributedByPart.createAggregation(context))
      .build();

    final Optional<AggregationBuilder> bucketLimitedHistogramAggregation =
      dateAggregationService.createUserTaskDateAggregation(dateAggContext);

    if (bucketLimitedHistogramAggregation.isPresent()) {
      final NestedAggregationBuilder groupByUserTaskDateAggregation =
        wrapInNestedUserTaskAggregation(
          context,
          bucketLimitedHistogramAggregation.get(),
          distributedByPart.createAggregation(context)
        );
      return Collections.singletonList(groupByUserTaskDateAggregation);
    }

    return Collections.emptyList();
  }

  private NestedAggregationBuilder wrapInNestedUserTaskAggregation(final ExecutionContext<ProcessReportDataDto> context,
                                                                   final AggregationBuilder aggregationToWrap,
                                                                   final AggregationBuilder distributedBySubAggregation) {
    return nested(USER_TASKS, USER_TASKS_AGGREGATION)
      .subAggregation(
        filter(FILTERED_USER_TASKS_AGGREGATION, createUserTaskAggregationFilter(context.getReportData()))
          .subAggregation(aggregationToWrap)
          .subAggregation(filter(FILTER_USER_TASKS_WITH_DATE_FIELD_SET_AGGREGATION, existsQuery(getDateField())))
      )
      // sibling aggregation for distributedByPart for retrieval of all keys that
      // should be present in distributedBy result
      .subAggregation(distributedBySubAggregation);
  }

  @Override
  public void addQueryResult(final CompositeCommandResult result,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    result.setGroups(processAggregations(response, context));
    result.setIsComplete(isResultComplete(response));
    result.setSorting(
      context.getReportConfiguration()
        .getSorting()
        .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC))
    );
  }

  private boolean isResultComplete(final SearchResponse response) {
    boolean complete = true;
    final Aggregations aggregations = response.getAggregations();
    if (aggregations != null) {
      final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
      final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
      if (filteredUserTasks.getAggregations().getAsMap().containsKey(FILTER_LIMITED_AGGREGATION)) {
        final ParsedFilter limitingAggregation = filteredUserTasks.getAggregations().get(FILTER_LIMITED_AGGREGATION);
        final ParsedFilter totalUserTasksWithDateFieldSetAgg =
          filteredUserTasks.getAggregations().get(FILTER_USER_TASKS_WITH_DATE_FIELD_SET_AGGREGATION);
        complete = limitingAggregation.getDocCount() == totalUserTasksWithDateFieldSetAgg.getDocCount();
      }
    }
    return complete;
  }

  private List<GroupByResult> processAggregations(final SearchResponse response,
                                                  final ExecutionContext<ProcessReportDataDto> context) {
    final Aggregations aggregations = response.getAggregations();

    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Optional<Aggregations> unwrappedLimitedAggregations =
      unwrapFilterLimitedAggregations(filteredUserTasks.getAggregations());

    distributedByPart.enrichContextWithAllExpectedDistributedByKeys(
      context,
      userTasks.getAggregations()
    );

    Map<String, Aggregations> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap = dateAggregationService.mapHistogramAggregationsToKeyAggregationMap(
        unwrappedLimitedAggregations.get(),
        context.getTimezone()
      );
    } else {
      keyToAggregationMap = dateAggregationService.mapRangeAggregationsToKeyAggregationMap(
        filteredUserTasks.getAggregations(),
        context.getTimezone()
      );
    }
    return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
  }

  protected abstract String getDateField();

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(final Map<String, Aggregations> keyToAggregationMap,
                                                             final SearchResponse response,
                                                             final ExecutionContext<ProcessReportDataDto> context) {
    return keyToAggregationMap
      .entrySet()
      .stream()
      .map(stringBucketEntry -> GroupByResult.createGroupByResult(
        stringBucketEntry.getKey(),
        distributedByPart.retrieveResult(response, stringBucketEntry.getValue(), context)
      ))
      .collect(Collectors.toList());
  }

  private GroupByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

}
