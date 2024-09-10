/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter.util;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.report.filter.util.DateHistogramFilterUtil.getMaxDateFilterOffsetDateTime;
import static io.camunda.optimize.service.db.report.filter.util.DateHistogramFilterUtil.getMinDateFilterOffsetDateTime;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.filter.QueryFilterES;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.filter.FilterContext;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.LongBounds;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateHistogramFilterUtilES {

  public static BoolQueryBuilder createModelElementDateHistogramLimitingFilterFor(
      final DateAggregationContextES context, final DateTimeFormatter dateTimeFormatter) {

    RangeQueryBuilder queryDate =
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
      final DateAggregationContextES context,
      final DateTimeFormatter dateFormatter) {

    final DecisionQueryFilterEnhancerES queryFilterEnhancer =
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
      final DateAggregationContextES context,
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
      final DateAggregationContextES context,
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
      final DateAggregationContextES context,
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
      final QueryFilterES<DateFilterDataDto<?>> queryFilter,
      final FilterContext filterContext) {
    final BoolQueryBuilder limitFilterQuery = boolQuery();
    queryFilter.addFilters(limitFilterQuery, filters, filterContext);
    return limitFilterQuery;
  }

  private static Optional<LongBounds> getExtendedBoundsFromDateFilters(
      final List<DateFilterDataDto<?>> dateFilters,
      final DateTimeFormatter dateFormatter,
      final DateAggregationContextES context) {
    // in case of several dateFilters, use min (oldest) one as start, and max (newest) one as end
    final Optional<OffsetDateTime> filterStart = getMinDateFilterOffsetDateTime(dateFilters);
    final OffsetDateTime filterEnd = getMaxDateFilterOffsetDateTime(dateFilters);
    return filterStart.map(
        start ->
            new LongBounds(
                dateFormatter.format(start.atZoneSameInstant(context.getTimezone())),
                dateFormatter.format(filterEnd.atZoneSameInstant(context.getTimezone()))));
  }
}
