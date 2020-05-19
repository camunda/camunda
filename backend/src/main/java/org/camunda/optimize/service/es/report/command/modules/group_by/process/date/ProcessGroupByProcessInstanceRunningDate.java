/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.RunningDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filters;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.getDateHistogramIntervalInMsFromMinMax;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByProcessInstanceRunningDate extends GroupByPart<ProcessReportDataDto> {
  private static final String RUNNING_DATE_FILTERS_NAME = "runningDateFilters";

  protected final DateTimeFormatter formatter;
  protected final ConfigurationService configurationService;
  protected final IntervalAggregationService intervalAggregationService;

  public ProcessGroupByProcessInstanceRunningDate(final DateTimeFormatter formatter,
                                                  final ConfigurationService configurationService,
                                                  final IntervalAggregationService intervalAggregationService) {
    this.formatter = formatter;
    this.configurationService = configurationService;
    this.intervalAggregationService = intervalAggregationService;
  }

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final Stats startDateStats = intervalAggregationService.getMinMaxStats(
      searchSourceBuilder.query(),
      PROCESS_INSTANCE_INDEX_NAME,
      START_DATE
    );
    final Stats endDateStats = intervalAggregationService.getMinMaxStats(
      searchSourceBuilder.query(),
      PROCESS_INSTANCE_INDEX_NAME,
      END_DATE
    );
    final GroupByDateUnit unit = getGroupByDateUnit(startDateStats, context.getReportData());
    // if the report contains no instances (stats are empty), no aggregations can be created as they are based on
    // instances data (start and end date stats)
    return startDateStats.getCount() == 0 || endDateStats.getCount() == 0
      ? Collections.emptyList()
      : createAggregation(
      OffsetDateTime.parse(startDateStats.getMinAsString(), formatter)
        .withOffsetSameInstant(OffsetDateTime.now().getOffset()),
      OffsetDateTime.parse(endDateStats.getMaxAsString(), formatter)
        .withOffsetSameInstant(OffsetDateTime.now().getOffset()),
      unit,
      context
    );
  }

  @Override
  protected void addQueryResult(final CompositeCommandResult result,
                                final SearchResponse response,
                                final ExecutionContext<ProcessReportDataDto> context) {
    if (response.getAggregations() != null) {
      // Only enrich result if aggregations exist (if no aggregations exist, this report contains no instances)
      result.setGroups(processAggregations(response, response.getAggregations(), context));
      result.setIsComplete(isResultComplete(response.getAggregations()));
      result.setSorting(
        context.getReportConfiguration()
          .getSorting()
          .orElseGet(() -> new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC))
      );
    }
  }

  private boolean isResultComplete(final Aggregations aggregations) {
    boolean complete = true;
    Filters agg = aggregations.get(RUNNING_DATE_FILTERS_NAME);
    if (agg.getBuckets().size() > configurationService.getEsAggregationBucketLimit()) {
      complete = false;
    }
    return complete;
  }

  private List<CompositeCommandResult.GroupByResult> processAggregations(
    final SearchResponse response,
    final Aggregations aggregations,
    final ExecutionContext<ProcessReportDataDto> context) {
    Filters agg = aggregations.get(RUNNING_DATE_FILTERS_NAME);

    List<CompositeCommandResult.GroupByResult> results = new ArrayList<>();

    for (Filters.Bucket entry : agg.getBuckets()) {
      String key = entry.getKeyAsString();
      final List<CompositeCommandResult.DistributedByResult> distributions =
        distributedByPart.retrieveResult(response, entry.getAggregations(), context);
      results.add(
        CompositeCommandResult.GroupByResult.createGroupByResult(key, distributions)
      );
      if (results.size() == configurationService.getEsAggregationBucketLimit()) {
        break;
      }
    }
    return results;
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setGroupBy(new RunningDateGroupByDto());
  }

  public List<AggregationBuilder> createAggregation(final OffsetDateTime startOfFirstInstance,
                                                    final OffsetDateTime endOfLastInstance,
                                                    final GroupByDateUnit unit,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    List<FiltersAggregator.KeyedFilter> filters = new ArrayList<>();
    final Duration automaticIntervalDuration = getDurationOfAutomaticInterval(
      startOfFirstInstance,
      endOfLastInstance
    );

    final OffsetDateTime startOfFirstBucket = truncateToUnit(startOfFirstInstance, unit);
    final OffsetDateTime endOfLastBucket = GroupByDateUnit.AUTOMATIC.equals(unit)
      ? endOfLastInstance
      : truncateToUnit(endOfLastInstance, unit).plus(1, mapToChronoUnit(unit));

    for (OffsetDateTime currentBucketStart = startOfFirstBucket;
         currentBucketStart.isBefore(endOfLastBucket);
         currentBucketStart = getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration)) {
      final String startAsString = formatter.format(currentBucketStart);
      final String endAsString = formatter.format(getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration));

      BoolQueryBuilder query = QueryBuilders.boolQuery()
        .must(
          QueryBuilders.rangeQuery(ProcessInstanceDto.Fields.startDate).lt(endAsString)
        )
        .must(
          QueryBuilders.boolQuery()
            .should(QueryBuilders.rangeQuery(ProcessInstanceDto.Fields.endDate).gte(startAsString))
            .should(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(ProcessInstanceDto.Fields.endDate)))
        );

      FiltersAggregator.KeyedFilter keyedFilter = new FiltersAggregator.KeyedFilter(startAsString, query);
      filters.add(keyedFilter);
    }

    return Collections.singletonList(
      AggregationBuilders
        .filters(
          RUNNING_DATE_FILTERS_NAME,
          filters.toArray(new FiltersAggregator.KeyedFilter[]{})
        )
        .subAggregation(distributedByPart.createAggregation(context)));
  }

  private OffsetDateTime truncateToUnit(final OffsetDateTime dateToTruncate,
                                        final GroupByDateUnit unit) {
    switch (unit) {
      case YEAR:
        return dateToTruncate
          .withMonth(1)
          .withDayOfMonth(1)
          .truncatedTo(ChronoUnit.DAYS);
      case MONTH:
        return dateToTruncate
          .withDayOfMonth(1)
          .truncatedTo(ChronoUnit.DAYS);
      case WEEK:
        return dateToTruncate
          .minusDays(getDistanceToStartOfWeekInDays(dateToTruncate))
          .truncatedTo(ChronoUnit.DAYS);
      case DAY:
      case HOUR:
      case MINUTE:
        return dateToTruncate.truncatedTo(mapToChronoUnit(unit));
      case AUTOMATIC:
        return dateToTruncate;
      default:
        throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }

  private Duration getDurationOfAutomaticInterval(final OffsetDateTime startOfFirstInstance,
                                                  final OffsetDateTime endOfLastInstance) {
    return Duration.of(
      getDateHistogramIntervalInMsFromMinMax(startOfFirstInstance, endOfLastInstance),
      ChronoUnit.MILLIS
    );
  }

  private OffsetDateTime getEndOfBucket(final OffsetDateTime startOfBucket,
                                        final GroupByDateUnit unit,
                                        final Duration durationOfAutomaticInterval) {
    return GroupByDateUnit.AUTOMATIC.equals(unit)
      ? startOfBucket.plus(durationOfAutomaticInterval)
      : startOfBucket.plus(1, mapToChronoUnit(unit));
  }

  /**
   * Currently, the start of a week in Optimize is Monday.
   * TODO: This will need adjusted with OPT-3162.
   */
  private int getDistanceToStartOfWeekInDays(final OffsetDateTime date) {
    return date.getDayOfWeek().getValue() - 1;
  }

  private GroupByDateUnit getGroupByDateUnit(final Stats stats,
                                             final ProcessReportDataDto processReportData) {
    // if there is only one instance and grouping is automatic, we always group by month instead
    GroupByDateUnit unit = ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
    return GroupByDateUnit.AUTOMATIC.equals(unit) && stats.getCount() == 1
      ? GroupByDateUnit.MONTH
      : unit;
  }
}
