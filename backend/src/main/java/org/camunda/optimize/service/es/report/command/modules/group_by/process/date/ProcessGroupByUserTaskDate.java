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
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.aggregations.metrics.StatsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.getNewLimitedStartDate;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.service.es.filter.UserTaskFilterQueryUtil.createUserTaskAggregationFilter;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Slf4j
@RequiredArgsConstructor
public abstract class ProcessGroupByUserTaskDate extends GroupByPart<ProcessReportDataDto> {

  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";
  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  private static final String RANGE_AGGREGATION = "rangeAggregation";
  private static final String STATS_AGGREGATION = "minMaxValueOfData";
  private static final String USER_TASK_MOST_RECENT_DATE_AGGREGATION = "userTaskMostRecentDateAggregation";
  private static final String FILTER_USER_TASKS_WITH_DATE_FIELD_SET_AGGREGATION =
    "filterUserTasksWithDateFieldSetAgg";

  private final DateTimeFormatter dateTimeFormatter;
  private final OptimizeElasticsearchClient esClient;
  private final IntervalAggregationService intervalAggregationService;
  private final ConfigurationService configurationService;

  @Override
  public Optional<Stats> calculateDateRangeForAutomaticGroupByDate(final ExecutionContext<ProcessReportDataDto> context,
                                                                   final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      DateGroupByValueDto groupByDate = (DateGroupByValueDto) context.getReportData().getGroupBy().getValue();
      if (GroupByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        Stats minMaxStats = getMinMaxStats(context, baseQuery, getDateField());
        return Optional.of(minMaxStats);
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

    final DateHistogramInterval interval = intervalAggregationService.getDateHistogramInterval(unit);
    final DateHistogramAggregationBuilder dateHistogramAggregation = AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(getDateField())
      .dateHistogramInterval(interval)
      .timeZone(ZoneId.systemDefault());

    final Optional<OffsetDateTime> latestDate =
      getMostRecentUserTaskDate(searchSourceBuilder.query(), getDateField(), context);
    final BoolQueryBuilder limitFilterQuery = createLimitFilterQuery(unit, latestDate.orElse(OffsetDateTime.now()));
    FilterAggregationBuilder bucketLimitedHistogramAggregation = wrapWithFilterLimitedParentAggregation(
      limitFilterQuery,
      dateHistogramAggregation.subAggregation(distributedByPart.createAggregation(context))
    );

    final NestedAggregationBuilder groupByUserTaskDateAggregation =
      wrapInNestedUserTaskAggregation(context, bucketLimitedHistogramAggregation);

    return Collections.singletonList(groupByUserTaskDateAggregation);
  }

  private BoolQueryBuilder createLimitFilterQuery(final GroupByDateUnit unit,
                                                  final OffsetDateTime endDate) {
    final BoolQueryBuilder limitFilterQuery = boolQuery();
    List<QueryBuilder> filters = limitFilterQuery.filter();

    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    final OffsetDateTime startDate = getNewLimitedStartDate(
      chronoUnit,
      configurationService.getEsAggregationBucketLimit(),
      endDate
    );

    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(getDateField());
    if (endDate != null) {
      queryDate.lte(dateTimeFormatter.format(endDate));
    }
    if (startDate != null) {
      queryDate.gte(dateTimeFormatter.format(startDate));
    }

    queryDate.format(OPTIMIZE_DATE_FORMAT);
    filters.add(queryDate);
    return limitFilterQuery;
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
      wrapInNestedUserTaskAggregation(context, mostRecentDateAggregation);

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

      for (Terms.Bucket bucket : terms.getBuckets()) {
        final String latestUserTaskDateAsString = bucket.getKeyAsString();
        return Optional.of(OffsetDateTime.from(dateTimeFormatter.parse(latestUserTaskDateAsString)));
      }
    } catch (IOException e) {
      log.warn("Could not retrieve startDate of latest user task!");
    }

    return Optional.empty();
  }

  private NestedAggregationBuilder wrapInNestedUserTaskAggregation(final ExecutionContext<ProcessReportDataDto> context,
                                                                   final AggregationBuilder aggregationToWrap) {
    return nested(USER_TASKS, USER_TASKS_AGGREGATION)
      .subAggregation(
        filter(FILTERED_USER_TASKS_AGGREGATION, createUserTaskAggregationFilter(context.getReportData()))
          .subAggregation(aggregationToWrap)
          .subAggregation(filter(FILTER_USER_TASKS_WITH_DATE_FIELD_SET_AGGREGATION, existsQuery(getDateField())))
      )
      // sibling aggregation for distributedByPart for retrieval of all keys that
      // should be present in distributedBy result
      .subAggregation(distributedByPart.createAggregation(context));
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
      return Optional.of(createIntervalAggregationFromGivenRange(context, field, min, max));
    } else {
      return createIntervalAggregation(context, query, field);
    }
  }

  private AggregationBuilder createIntervalAggregationFromGivenRange(final ExecutionContext<ProcessReportDataDto> context,
                                                                     String field, OffsetDateTime min,
                                                                     OffsetDateTime max) {
    long msAsUnit = getDateHistogramIntervalFromMinMax(min, max);
    RangeAggregationBuilder rangeAgg = AggregationBuilders
      .range(RANGE_AGGREGATION)
      .field(field);
    AggregationBuilder nestedRangeAgg =
      wrapInNestedUserTaskAggregation(context, rangeAgg.subAggregation(distributedByPart.createAggregation(context)));

    for (OffsetDateTime start = min; start.isBefore(max); start = start.plus(msAsUnit, ChronoUnit.MILLIS)) {
      OffsetDateTime nextStart = start.plus(msAsUnit, ChronoUnit.MILLIS);
      boolean isLast = nextStart.isAfter(max) || nextStart.isEqual(max);
      // plus 1 millisecond because the end of the range is inclusive
      OffsetDateTime end = isLast ? max.plus(1, ChronoUnit.MILLIS) : nextStart;

      RangeAggregator.Range range =
        new RangeAggregator.Range(
          dateTimeFormatter.format(start.atZoneSameInstant(ZoneId.systemDefault())), // key that's being used
          dateTimeFormatter.format(start),
          dateTimeFormatter.format(end)
        );
      rangeAgg.addRange(range);
    }
    return nestedRangeAgg;
  }

  private long getDateHistogramIntervalFromMinMax(OffsetDateTime min, OffsetDateTime max) {
    long minInMs = min.toInstant().toEpochMilli();
    long maxInMs = max.toInstant().toEpochMilli();
    final long intervalFromMinToMax = (maxInMs - minInMs) / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    // we need to ensure that the interval is > 1 since we create the range buckets based on this
    // interval and it will cause an endless loop if the interval is 0.
    return Math.max(intervalFromMinToMax, 1);
  }

  private Optional<AggregationBuilder> createIntervalAggregation(final ExecutionContext<ProcessReportDataDto> context,
                                                                 final QueryBuilder query,
                                                                 final String field) {
    Stats stats = getMinMaxStats(context, query, field);
    if (stats.getCount() > 1) {
      OffsetDateTime min = OffsetDateTime.parse(stats.getMinAsString(), dateTimeFormatter);
      OffsetDateTime max = OffsetDateTime.parse(stats.getMaxAsString(), dateTimeFormatter);
      return Optional.of(createIntervalAggregationFromGivenRange(context, field, min, max));
    } else {
      return Optional.empty();
    }
  }

  private Stats getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context,
                               final QueryBuilder query,
                               final String field) {

    final StatsAggregationBuilder minMaxOfUserTaskDate = AggregationBuilders
      .stats(STATS_AGGREGATION)
      .field(field)
      .format(OPTIMIZE_DATE_FORMAT);
    final NestedAggregationBuilder statsAgg =
      wrapInNestedUserTaskAggregation(context, minMaxOfUserTaskDate);

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
    return filteredUserTasks.getAggregations().get(STATS_AGGREGATION);
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
        .orElseGet(() -> new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC))
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

    List<GroupByResult> result = new ArrayList<>();
    if (unwrappedLimitedAggregations.isPresent()) {
      final Aggregations filterLimitedAggregation = unwrappedLimitedAggregations.get();
      final Histogram agg = filterLimitedAggregation.get(DATE_HISTOGRAM_AGGREGATION);

      for (Histogram.Bucket entry : agg.getBuckets()) {
        ZonedDateTime keyAsDate = (ZonedDateTime) entry.getKey();
        String formattedDate = keyAsDate.withZoneSameInstant(ZoneId.systemDefault()).format(dateTimeFormatter);
        final List<DistributedByResult> distributions =
          distributedByPart.retrieveResult(response, entry.getAggregations(), context);
        result.add(GroupByResult.createGroupByResult(formattedDate, distributions));
      }
    } else {
      result = processAutomaticIntervalAggregations(response, filteredUserTasks.getAggregations(), context);
    }
    return result;
  }

  private List<GroupByResult> processAutomaticIntervalAggregations(final SearchResponse response,
                                                                   final Aggregations aggregations,
                                                                   final ExecutionContext<ProcessReportDataDto> context) {
    return mapIntervalAggregationsToKeyBucketMap(aggregations)
      .entrySet()
      .stream()
      .map(stringBucketEntry -> GroupByResult.createGroupByResult(
        stringBucketEntry.getKey(),
        distributedByPart.retrieveResult(response, stringBucketEntry.getValue().getAggregations(), context)
      ))
      .collect(Collectors.toList());
  }

  private Map<String, Range.Bucket> mapIntervalAggregationsToKeyBucketMap(Aggregations aggregations) {
    Range agg = aggregations.get(RANGE_AGGREGATION);

    Map<String, Range.Bucket> result = new LinkedHashMap<>();
    for (Range.Bucket entry : agg.getBuckets()) {
      String formattedDate = entry.getKeyAsString();
      result.put(formattedDate, entry);
    }
    // sort in descending order
    result = result.entrySet().stream()
      .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
        throw new IllegalStateException(String.format("Duplicate key %s", u));
      }, LinkedHashMap::new));
    return result;
  }

  protected abstract String getDateField();

  private GroupByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

}
