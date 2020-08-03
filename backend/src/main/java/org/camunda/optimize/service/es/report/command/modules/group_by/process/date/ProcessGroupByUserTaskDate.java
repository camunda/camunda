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
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.util.DateAggregationService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.aggregations.metrics.StatsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Slf4j
@RequiredArgsConstructor
public abstract class ProcessGroupByUserTaskDate extends GroupByPart<ProcessReportDataDto> {

  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";

  private static final String STATS_AGGREGATION = "minMaxValueOfData";
  private static final String USER_TASK_MOST_RECENT_DATE_AGGREGATION = "userTaskMostRecentDateAggregation";
  private static final String FILTER_USER_TASKS_WITH_DATE_FIELD_SET_AGGREGATION =
    "filterUserTasksWithDateFieldSetAgg";

  private final DateTimeFormatter dateTimeFormatter;
  private final OptimizeElasticsearchClient esClient;
  private final DateAggregationService dateAggregationService;

  @Override
  public Optional<MinMaxStatDto> calculateDateRangeForAutomaticGroupByDate(final ExecutionContext<ProcessReportDataDto> context,
                                                                           final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      DateGroupByValueDto groupByDate = (DateGroupByValueDto) context.getReportData().getGroupBy().getValue();
      if (GroupByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(
          getMinMaxStats(context, baseQuery, getDateField())
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
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      return createAutomaticIntervalAggregation(searchSourceBuilder, context);
    }

    final Optional<OffsetDateTime> latestDate =
      getMostRecentUserTaskDate(searchSourceBuilder.query(), getDateField(), context);

    final AggregationBuilder bucketLimitedHistogramAggregation =
      dateAggregationService.createFilterLimitedUserTaskDateHistogramWithSubAggregation(
        unit,
        getDateField(),
        context.getTimezone(),
        latestDate.orElse(OffsetDateTime.now()),
        distributedByPart.createAggregation(context)
      );

    final NestedAggregationBuilder groupByUserTaskDateAggregation =
      wrapInNestedUserTaskAggregation(
        context,
        bucketLimitedHistogramAggregation,
        distributedByPart.createAggregation(context)
      );

    return Collections.singletonList(groupByUserTaskDateAggregation);
  }

  private Optional<OffsetDateTime> getMostRecentUserTaskDate(final QueryBuilder baseQuery,
                                                             final String dateField,
                                                             final ExecutionContext<ProcessReportDataDto> context) {
    final TermsAggregationBuilder mostRecentDateAggregation = terms(USER_TASK_MOST_RECENT_DATE_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(dateField)
      .format(OPTIMIZE_DATE_FORMAT)
      .size(1);
    final NestedAggregationBuilder groupByUserTaskDateAggregation =
      wrapInNestedUserTaskAggregation(context, mostRecentDateAggregation, distributedByPart.createAggregation(context));

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .sort(SortBuilders.fieldSort(dateField).order(org.elasticsearch.search.sort.SortOrder.DESC))
      .aggregation(groupByUserTaskDateAggregation)
      .size(0);
    final SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME).source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      final Nested userTasks = response.getAggregations().get(USER_TASKS_AGGREGATION);
      final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
      final Terms terms = filteredUserTasks.getAggregations().get(USER_TASK_MOST_RECENT_DATE_AGGREGATION);

      if (!terms.getBuckets().isEmpty()) {
        final String latestUserTaskDateAsString = terms.getBuckets().get(0).getKeyAsString();
        return Optional.of(OffsetDateTime.from(dateTimeFormatter.parse(latestUserTaskDateAsString)));
      }
    } catch (IOException e) {
      log.warn("Could not retrieve startDate of latest user task!");
    }

    return Optional.empty();
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

  private List<AggregationBuilder> createAutomaticIntervalAggregation(final SearchSourceBuilder builder,
                                                                      final ExecutionContext<ProcessReportDataDto> context) {

    Optional<AggregationBuilder> automaticIntervalAggregation =
      createIntervalAggregation(
        context,
        context.getDateIntervalRange(),
        builder.query(),
        getDateField()
      );

    return automaticIntervalAggregation
      .map(Collections::singletonList)
      .orElseGet(() -> createAggregation(builder, context, GroupByDateUnit.MONTH));
  }

  private Optional<AggregationBuilder> createIntervalAggregation(final ExecutionContext<ProcessReportDataDto> context,
                                                                 Optional<org.apache.commons.lang3.Range<OffsetDateTime>> rangeToUse,
                                                                 QueryBuilder query,
                                                                 String field) {
    if (rangeToUse.isPresent()) {
      OffsetDateTime min = rangeToUse.get().getMinimum();
      OffsetDateTime max = rangeToUse.get().getMaximum();
      return Optional.of(createNestedIntervalAggregationFromGivenRange(context, field, min, max));
    } else {
      return createIntervalAggregation(context, query, field);
    }
  }

  private Optional<AggregationBuilder> createIntervalAggregation(final ExecutionContext<ProcessReportDataDto> context,
                                                                 final QueryBuilder query,
                                                                 final String field) {
    MinMaxStatDto stats = getMinMaxStats(context, query, field);
    if (stats.isValidRange()) {
      OffsetDateTime min = OffsetDateTime.parse(stats.getMinAsString(), dateTimeFormatter);
      OffsetDateTime max = OffsetDateTime.parse(stats.getMaxAsString(), dateTimeFormatter);
      return Optional.of(createNestedIntervalAggregationFromGivenRange(context, field, min, max));
    } else {
      return Optional.empty();
    }
  }

  private AggregationBuilder createNestedIntervalAggregationFromGivenRange(final ExecutionContext<ProcessReportDataDto> context,
                                                                           final String field,
                                                                           final OffsetDateTime min,
                                                                           final OffsetDateTime max) {
    AggregationBuilder rangeAgg = dateAggregationService.createIntervalAggregationFromGivenRange(
      field,
      context.getTimezone(),
      min,
      max
    );
    return wrapInNestedUserTaskAggregation(
      context,
      rangeAgg.subAggregation(distributedByPart.createAggregation(context)),
      distributedByPart.createAggregation(context)
    );
  }

  private MinMaxStatDto getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context,
                                       final QueryBuilder query,
                                       final String field) {
    final StatsAggregationBuilder minMaxOfUserTaskDate = AggregationBuilders
      .stats(STATS_AGGREGATION)
      .field(field)
      .format(OPTIMIZE_DATE_FORMAT);
    final NestedAggregationBuilder statsAgg =
      wrapInNestedUserTaskAggregation(context, minMaxOfUserTaskDate, distributedByPart.createAggregation(context));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(statsAgg)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Could not automatically determine interval of group by date on field [" + field + "]!";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    final Nested userTasks = response.getAggregations().get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Stats minMaxStats = filteredUserTasks.getAggregations().get(STATS_AGGREGATION);
    return new MinMaxStatDto(
      minMaxStats.getMin(),
      minMaxStats.getMax(),
      minMaxStats.getMinAsString(),
      minMaxStats.getMaxAsString()
    );
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
    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    if (filteredUserTasks.getAggregations().getAsMap().containsKey(FILTER_LIMITED_AGGREGATION)) {
      final ParsedFilter limitingAggregation = filteredUserTasks.getAggregations().get(FILTER_LIMITED_AGGREGATION);
      final ParsedFilter totalUserTasksWithDateFieldSetAgg =
        filteredUserTasks.getAggregations().get(FILTER_USER_TASKS_WITH_DATE_FIELD_SET_AGGREGATION);
      complete = limitingAggregation.getDocCount() == totalUserTasksWithDateFieldSetAgg.getDocCount();
    }
    return complete;
  }

  private List<GroupByResult> processAggregations(final SearchResponse response,
                                                  final ExecutionContext<ProcessReportDataDto> context) {
    final Aggregations aggregations = response.getAggregations();
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
