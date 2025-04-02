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

import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.ExtendedBounds;
import co.elastic.clients.elasticsearch._types.aggregations.FieldDateMath;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.filter.QueryFilterES;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.filter.FilterContext;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateHistogramFilterUtilES {

  public static BoolQuery.Builder createModelElementDateHistogramLimitingFilterFor(
      final DateAggregationContextES context, final DateTimeFormatter dateTimeFormatter) {

    final BoolQuery.Builder queryDate = new BoolQuery.Builder();
    queryDate.filter(
        f ->
            f.range(
                r ->
                    r.date(
                        d ->
                            d.field(context.getDateField())
                                .gte(dateTimeFormatter.format(context.getEarliestDate()))
                                .lte(dateTimeFormatter.format(context.getLatestDate()))
                                .format(OPTIMIZE_DATE_FORMAT))));
    return queryDate;
  }

  public static BoolQuery.Builder extendBoundsAndCreateDecisionDateHistogramLimitingFilterFor(
      final DateHistogramAggregation.Builder dateHistogramAggregation,
      final DateAggregationContextES context,
      final DateTimeFormatter dateFormatter) {

    final DecisionQueryFilterEnhancerES queryFilterEnhancer =
        context.getDecisionQueryFilterEnhancer();
    final List<DateFilterDataDto<?>> evaluationDateFilter =
        queryFilterEnhancer.extractFilters(
            context.getDecisionFilters(), EvaluationDateFilterDto.class);

    final BoolQuery.Builder limitFilterQuery =
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

  public static BoolQuery.Builder extendBoundsAndCreateProcessDateHistogramLimitingFilterFor(
      final DateHistogramAggregation.Builder dateHistogramAggregation,
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

  private static BoolQuery.Builder extendBoundsAndCreateProcessStartDateHistogramLimitingFilterFor(
      final DateHistogramAggregation.Builder dateHistogramAggregation,
      final DateAggregationContextES context,
      final DateTimeFormatter dateTimeFormatter) {

    final ProcessQueryFilterEnhancerES queryFilterEnhancer =
        context.getProcessQueryFilterEnhancer();

    final List<DateFilterDataDto<?>> startDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceStartDateFilterDto.class);
    final List<DateFilterDataDto<?>> endDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceEndDateFilterDto.class);

    // if custom end filters and no startDateFilters are present, limit based on them
    final BoolQuery.Builder limitFilterQuery;
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

  private static BoolQuery.Builder extendBoundsAndCreateProcessEndDateHistogramLimitingFilterFor(
      final DateHistogramAggregation.Builder dateHistogramAggregation,
      final DateAggregationContextES context,
      final DateTimeFormatter dateTimeFormatter) {

    final ProcessQueryFilterEnhancerES queryFilterEnhancer =
        context.getProcessQueryFilterEnhancer();

    final List<DateFilterDataDto<?>> startDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceStartDateFilterDto.class);
    final List<DateFilterDataDto<?>> endDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceEndDateFilterDto.class);

    // if custom start filters and no endDateFilters are present, limit based on them
    final BoolQuery.Builder limitFilterQuery;
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

  public static BoolQuery.Builder createFilterBoolQueryBuilder(
      final List<DateFilterDataDto<?>> filters,
      final QueryFilterES<DateFilterDataDto<?>> queryFilter,
      final FilterContext filterContext) {
    final BoolQuery.Builder limitFilterQuery = new BoolQuery.Builder();
    queryFilter.addFilters(limitFilterQuery, filters, filterContext);
    return limitFilterQuery;
  }

  private static Optional<ExtendedBounds<FieldDateMath>> getExtendedBoundsFromDateFilters(
      final List<DateFilterDataDto<?>> dateFilters,
      final DateTimeFormatter dateFormatter,
      final DateAggregationContextES context) {
    // in case of several dateFilters, use min (oldest) one as start, and max (newest) one as end
    final Optional<OffsetDateTime> filterStart = getMinDateFilterOffsetDateTime(dateFilters);
    final OffsetDateTime filterEnd = getMaxDateFilterOffsetDateTime(dateFilters);
    return filterStart.map(
        start ->
            ExtendedBounds.of(
                e ->
                    e.min(
                            FieldDateMath.of(
                                f ->
                                    f.value(
                                        (double)
                                            start
                                                .atZoneSameInstant(context.getTimezone())
                                                .toInstant()
                                                .toEpochMilli())))
                        .max(
                            FieldDateMath.of(
                                f ->
                                    f.value(
                                        (double)
                                            filterEnd
                                                .atZoneSameInstant(context.getTimezone())
                                                .toInstant()
                                                .toEpochMilli())))));
  }
}
