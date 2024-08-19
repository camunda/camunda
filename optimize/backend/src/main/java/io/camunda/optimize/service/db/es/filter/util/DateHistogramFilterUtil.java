/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter.util;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.util.DateFilterUtil.getStartOfCurrentInterval;
import static io.camunda.optimize.service.util.DateFilterUtil.getStartOfPreviousInterval;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.filter.FilterContext;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.filter.QueryFilter;
import io.camunda.optimize.service.db.es.report.command.util.DateAggregationContext;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.LongBounds;

public class DateHistogramFilterUtil {

  private DateHistogramFilterUtil() {}

  public static BoolQueryBuilder createModelElementDateHistogramLimitingFilterFor(
      final DateAggregationContext context, final DateTimeFormatter dateTimeFormatter) {

    final RangeQueryBuilder queryDate =
        QueryBuilders.rangeQuery(context.getDateField())
            .gte(dateTimeFormatter.format(context.getEarliestDate()))
            .lte(dateTimeFormatter.format(context.getLatestDate()))
            .format(OPTIMIZE_DATE_FORMAT);
    final BoolQueryBuilder limitFilterQuery = boolQuery();
    limitFilterQuery.filter().add(queryDate);
    return limitFilterQuery;
  }

  public static BoolQueryBuilder extendBoundsAndCreateDecisionDateHistogramLimitingFilterFor(
      final DateHistogramAggregationBuilder dateHistogramAggregation,
      final DateAggregationContext context,
      final DateTimeFormatter dateFormatter) {

    final DecisionQueryFilterEnhancer queryFilterEnhancer =
        context.getDecisionQueryFilterEnhancer();
    final List<DateFilterDataDto<?>> evaluationDateFilter =
        queryFilterEnhancer.extractFilters(
            context.getDecisionFilters(), EvaluationDateFilterDto.class);

    final BoolQueryBuilder limitFilterQuery =
        createFilterBoolQueryBuilder(
            evaluationDateFilter,
            queryFilterEnhancer.getEvaluationDateQueryFilter(),
            context.getFilterContext());

    if (!evaluationDateFilter.isEmpty()) {
      getExtendedBoundsFromDateFilters(evaluationDateFilter, dateFormatter, context)
          .ifPresent(dateHistogramAggregation::extendedBounds);
    }
    return limitFilterQuery;
  }

  public static BoolQueryBuilder extendBoundsAndCreateProcessDateHistogramLimitingFilterFor(
      final DateHistogramAggregationBuilder dateHistogramAggregation,
      final DateAggregationContext context,
      final DateTimeFormatter dateTimeFormatter) {
    if (context.isStartDateAggregation()) {
      return extendBoundsAndCreateProcessStartDateHistogramLimitingFilterFor(
          dateHistogramAggregation, context, dateTimeFormatter);
    } else {
      return extendBoundsAndCreateProcessEndDateHistogramLimitingFilterFor(
          dateHistogramAggregation, context, dateTimeFormatter);
    }
  }

  private static BoolQueryBuilder extendBoundsAndCreateProcessStartDateHistogramLimitingFilterFor(
      final DateHistogramAggregationBuilder dateHistogramAggregation,
      final DateAggregationContext context,
      final DateTimeFormatter dateTimeFormatter) {

    final ProcessQueryFilterEnhancer queryFilterEnhancer = context.getProcessQueryFilterEnhancer();

    final List<DateFilterDataDto<?>> startDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceStartDateFilterDto.class);
    final List<DateFilterDataDto<?>> endDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceEndDateFilterDto.class);

    // if custom end filters and no startDateFilters are present, limit based on them
    final BoolQueryBuilder limitFilterQuery;
    if (!endDateFilters.isEmpty() && startDateFilters.isEmpty()) {
      limitFilterQuery =
          createFilterBoolQueryBuilder(
              endDateFilters,
              queryFilterEnhancer.getInstanceEndDateQueryFilter(),
              context.getFilterContext());
    } else {
      if (!startDateFilters.isEmpty()) {
        getExtendedBoundsFromDateFilters(startDateFilters, dateTimeFormatter, context)
            .ifPresent(dateHistogramAggregation::extendedBounds);
      }
      limitFilterQuery =
          createFilterBoolQueryBuilder(
              startDateFilters,
              queryFilterEnhancer.getInstanceStartDateQueryFilter(),
              context.getFilterContext());
    }
    return limitFilterQuery;
  }

  private static BoolQueryBuilder extendBoundsAndCreateProcessEndDateHistogramLimitingFilterFor(
      final DateHistogramAggregationBuilder dateHistogramAggregation,
      final DateAggregationContext context,
      final DateTimeFormatter dateTimeFormatter) {

    final ProcessQueryFilterEnhancer queryFilterEnhancer = context.getProcessQueryFilterEnhancer();

    final List<DateFilterDataDto<?>> startDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceStartDateFilterDto.class);
    final List<DateFilterDataDto<?>> endDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceEndDateFilterDto.class);

    // if custom start filters and no endDateFilters are present, limit based on them
    final BoolQueryBuilder limitFilterQuery;
    if (endDateFilters.isEmpty() && !startDateFilters.isEmpty()) {
      limitFilterQuery =
          createFilterBoolQueryBuilder(
              startDateFilters,
              queryFilterEnhancer.getInstanceStartDateQueryFilter(),
              context.getFilterContext());
    } else {
      if (!endDateFilters.isEmpty()) {
        // extend bounds of histogram to include entire range in filter
        getExtendedBoundsFromDateFilters(endDateFilters, dateTimeFormatter, context)
            .ifPresent(dateHistogramAggregation::extendedBounds);
      }

      limitFilterQuery =
          createFilterBoolQueryBuilder(
              endDateFilters,
              queryFilterEnhancer.getInstanceEndDateQueryFilter(),
              context.getFilterContext());
    }

    return limitFilterQuery;
  }

  public static BoolQueryBuilder createFilterBoolQueryBuilder(
      final List<DateFilterDataDto<?>> filters,
      final QueryFilter<DateFilterDataDto<?>> queryFilter,
      final FilterContext filterContext) {
    final BoolQueryBuilder limitFilterQuery = boolQuery();
    queryFilter.addFilters(limitFilterQuery, filters, filterContext);
    return limitFilterQuery;
  }

  private static Optional<LongBounds> getExtendedBoundsFromDateFilters(
      final List<DateFilterDataDto<?>> dateFilters,
      final DateTimeFormatter dateFormatter,
      final DateAggregationContext context) {
    // in case of several dateFilters, use min (oldest) one as start, and max (newest) one as end
    final Optional<OffsetDateTime> filterStart = getMinDateFilterOffsetDateTime(dateFilters);
    final OffsetDateTime filterEnd = getMaxDateFilterOffsetDateTime(dateFilters);
    return filterStart.map(
        start ->
            new LongBounds(
                dateFormatter.format(start.atZoneSameInstant(context.getTimezone())),
                dateFormatter.format(filterEnd.atZoneSameInstant(context.getTimezone()))));
  }

  private static OffsetDateTime getMaxDateFilterOffsetDateTime(
      final List<DateFilterDataDto<?>> dateFilters) {
    return dateFilters.stream()
        .map(DateFilterDataDto::getEnd)
        .filter(Objects::nonNull)
        .max(OffsetDateTime::compareTo)
        .orElse(OffsetDateTime.now());
  }

  private static Optional<OffsetDateTime> getMinDateFilterOffsetDateTime(
      final List<DateFilterDataDto<?>> dateFilters) {
    final OffsetDateTime now = OffsetDateTime.now();
    return Stream.of(
            dateFilters.stream()
                .filter(FixedDateFilterDataDto.class::isInstance)
                .map(date -> (OffsetDateTime) date.getStart())
                .filter(Objects::nonNull), // only consider fixed date filters with a set start
            dateFilters.stream()
                .filter(RollingDateFilterDataDto.class::isInstance)
                .map(
                    filter -> {
                      final RollingDateFilterStartDto startDto =
                          (RollingDateFilterStartDto) filter.getStart();
                      final ChronoUnit filterUnit = ChronoUnit.valueOf(startDto.getUnit().name());
                      return now.minus(startDto.getValue(), filterUnit);
                    }),
            dateFilters.stream()
                .filter(RelativeDateFilterDataDto.class::isInstance)
                .map(
                    filter -> {
                      final RelativeDateFilterStartDto startDto =
                          ((RelativeDateFilterDataDto) filter).getStart();
                      final OffsetDateTime startOfCurrentInterval =
                          getStartOfCurrentInterval(now, startDto.getUnit());
                      if (startDto.getValue() == 0L) {
                        return startOfCurrentInterval;
                      } else {
                        return getStartOfPreviousInterval(
                            startOfCurrentInterval, startDto.getUnit(), startDto.getValue());
                      }
                    }))
        .flatMap(stream -> stream)
        .min(OffsetDateTime::compareTo);
  }
}
