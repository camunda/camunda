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
import org.camunda.optimize.service.es.report.MinMaxStatDto;
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
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
  public Optional<MinMaxStatDto> calculateDateRangeForAutomaticGroupByDate(final ExecutionContext<ProcessReportDataDto> context,
                                                                           final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      DateGroupByValueDto groupByDate = (DateGroupByValueDto) context.getReportData().getGroupBy().getValue();
      if (GroupByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(
          intervalAggregationService.getCrossFieldMinMaxStats(
            baseQuery,
            PROCESS_INSTANCE_INDEX_NAME,
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
    final MinMaxStatDto minMaxStats = intervalAggregationService.getCrossFieldMinMaxStats(
      searchSourceBuilder.query(),
      PROCESS_INSTANCE_INDEX_NAME,
      START_DATE,
      END_DATE
    );
    final GroupByDateUnit unit = getGroupByDateUnit(minMaxStats, context.getReportData());
    if (!minMaxStats.isMinValid()) {
      // if the report contains no instances, no aggregations can be created as they are
      // based on instances data (start and end date stats)
      return Collections.emptyList();
    }

    final OffsetDateTime startOfRange = OffsetDateTime.parse(minMaxStats.getMinAsString(), formatter)
      .atZoneSameInstant(context.getTimezone()).toOffsetDateTime();
    final OffsetDateTime endOfRange = OffsetDateTime.parse(minMaxStats.getMaxAsString(), formatter)
      .atZoneSameInstant(context.getTimezone()).toOffsetDateTime();

    return createAggregation(
      startOfRange,
      endOfRange,
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
          .orElseGet(() -> new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.ASC))
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
      String key = formatToCorrectTimezone(entry.getKeyAsString(), context.getTimezone());
      final List<CompositeCommandResult.DistributedByResult> distributions =
        distributedByPart.retrieveResult(response, entry.getAggregations(), context);
      results.add(
        CompositeCommandResult.GroupByResult.createGroupByResult(key, distributions)
      );
      if (results.size() >= configurationService.getEsAggregationBucketLimit()) {
        break;
      }
    }
    return results;
  }

  private String formatToCorrectTimezone(final String dateAsString, final ZoneId timezone) {
    final OffsetDateTime date = OffsetDateTime.parse(dateAsString, formatter);
    OffsetDateTime dateWithAdjustedTimezone = date.atZoneSameInstant(timezone).toOffsetDateTime();
    return formatter.format(dateWithAdjustedTimezone);
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

    // to do correct date arithmetic (e.g. daylight saving time, timezones, etc.) we need to switch
    // here to zoned date time.
    final ZonedDateTime startOfFirstBucket = truncateToUnit(startOfFirstInstance, unit, context.getTimezone());
    final ZonedDateTime endOfLastBucket = GroupByDateUnit.AUTOMATIC.equals(unit)
      ? endOfLastInstance.atZoneSameInstant(context.getTimezone())
      : truncateToUnit(endOfLastInstance, unit, context.getTimezone()).plus(1, mapToChronoUnit(unit));

    for (ZonedDateTime currentBucketStart = startOfFirstBucket;
         currentBucketStart.isBefore(endOfLastBucket);
         currentBucketStart = getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration)) {
      // to use our correct date formatting we need to switch back to OffsetDateTime
      final String startAsString = formatter.format(currentBucketStart.toOffsetDateTime());
      final String endAsString = formatter.format(getEndOfBucket(
        currentBucketStart,
        unit,
        automaticIntervalDuration
      ).toOffsetDateTime());

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

  private ZonedDateTime truncateToUnit(final OffsetDateTime dateToTruncate,
                                       final GroupByDateUnit unit,
                                       final ZoneId timezone) {
    switch (unit) {
      case YEAR:
        return dateToTruncate
          .atZoneSameInstant(timezone)
          .withMonth(1)
          .withDayOfMonth(1)
          .truncatedTo(ChronoUnit.DAYS);
      case MONTH:
        return dateToTruncate
          .atZoneSameInstant(timezone)
          .withDayOfMonth(1)
          .truncatedTo(ChronoUnit.DAYS);
      case WEEK:
        return dateToTruncate
          .atZoneSameInstant(timezone)
          .minusDays(getDistanceToStartOfWeekInDays(dateToTruncate))
          .truncatedTo(ChronoUnit.DAYS);
      case DAY:
      case HOUR:
      case MINUTE:
        return dateToTruncate.atZoneSameInstant(timezone).truncatedTo(mapToChronoUnit(unit));
      case AUTOMATIC:
        return dateToTruncate.atZoneSameInstant(timezone);
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

  private ZonedDateTime getEndOfBucket(final ZonedDateTime startOfBucket,
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

  private GroupByDateUnit getGroupByDateUnit(final MinMaxStatDto stats,
                                             final ProcessReportDataDto processReportData) {
    // if there is only one instance data point and grouping is automatic, we always group by month instead
    GroupByDateUnit unit = ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
    return GroupByDateUnit.AUTOMATIC.equals(unit) && !stats.isValidRange()
      ? GroupByDateUnit.MONTH
      : unit;
  }
}
