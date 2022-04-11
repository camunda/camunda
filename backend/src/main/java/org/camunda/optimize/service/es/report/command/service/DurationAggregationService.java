/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.service;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.BucketUnit;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.util.DurationScriptUtil;
import org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.ScriptQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.camunda.optimize.es.aggregations.NumberHistogramAggregationUtil.generateHistogramFromScript;
import static org.camunda.optimize.service.es.filter.util.ModelElementFilterQueryUtil.createUserTaskFlowNodeTypeFilter;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;
import static org.camunda.optimize.service.util.RoundingUtil.roundDownToNearestPowerOfTen;
import static org.camunda.optimize.service.util.RoundingUtil.roundUpToNearestPowerOfTen;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;


@Component
@AllArgsConstructor
public class DurationAggregationService {
  private static final String DURATION_HISTOGRAM_AGGREGATION = "durationHistogram";
  private static final int AUTOMATIC_BUCKET_LIMIT = NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
  private static final BucketUnit DEFAULT_UNIT = BucketUnit.MILLISECOND;
  private static final DurationUnit FILTER_UNIT = DurationUnit.MILLIS;

  private final MinMaxStatsService minMaxStatsService;

  public Optional<AggregationBuilder> createLimitedGroupByScriptedDurationAggregation(
    final SearchSourceBuilder searchSourceBuilder,
    final ExecutionContext<ProcessReportDataDto> context,
    final DistributedByPart<ProcessReportDataDto> distributedByPart,
    final Script durationCalculationScript) {

    final MinMaxStatDto minMaxStats = minMaxStatsService.getMinMaxNumberRangeForScriptedField(
      context,
      searchSourceBuilder.query(),
      getIndexNames(context),
      durationCalculationScript
    );
    return createLimitedGroupByScriptedDurationAggregation(
      context, distributedByPart, durationCalculationScript, minMaxStats, this::createProcessInstanceLimitingFilterQuery
    );
  }

  public Optional<AggregationBuilder> createLimitedGroupByScriptedEventDurationAggregation(
    final SearchSourceBuilder searchSourceBuilder,
    final ExecutionContext<ProcessReportDataDto> context,
    final DistributedByPart<ProcessReportDataDto> distributedByPart,
    final Script durationCalculationScript) {

    final MinMaxStatDto minMaxStats = minMaxStatsService.getMinMaxNumberRangeForNestedScriptedField(
      context, searchSourceBuilder.query(), getIndexNames(context), FLOW_NODE_INSTANCES, durationCalculationScript
    );
    return createLimitedGroupByScriptedDurationAggregation(
      context, distributedByPart, durationCalculationScript, minMaxStats, this::createEventLimitingFilterQuery
    );
  }

  public Optional<AggregationBuilder> createLimitedGroupByScriptedUserTaskDurationAggregation(
    final SearchSourceBuilder searchSourceBuilder,
    final ExecutionContext<ProcessReportDataDto> context,
    final DistributedByPart<ProcessReportDataDto> distributedByPart,
    final Script durationCalculationScript,
    final UserTaskDurationTime userTaskDurationTime) {

    final MinMaxStatDto minMaxStats = minMaxStatsService.getMinMaxNumberRangeForNestedScriptedField(
      context,
      searchSourceBuilder.query(),
      getIndexNames(context),
      FLOW_NODE_INSTANCES,
      durationCalculationScript,
      createUserTaskFlowNodeTypeFilter()
    );
    return createLimitedGroupByScriptedDurationAggregation(
      context,
      distributedByPart,
      durationCalculationScript,
      minMaxStats,
      (filterOperator, filterValueInMillis) -> createUserTaskLimitingFilterQuery(
        filterOperator, userTaskDurationTime, filterValueInMillis
      )
    );
  }

  public List<CompositeCommandResult.GroupByResult> mapGroupByDurationResults(
    final SearchResponse response,
    final Aggregations parentSubAggregations,
    final ExecutionContext<ProcessReportDataDto> context,
    final DistributedByPart<ProcessReportDataDto> distributedByPart) {
    final List<CompositeCommandResult.GroupByResult> durationHistogramData = new ArrayList<>();
    final Optional<Histogram> histogramAggregationResult = Optional.ofNullable(parentSubAggregations)
      .flatMap(FilterLimitedAggregationUtil::unwrapFilterLimitedAggregations)
      .map(aggregations -> aggregations.get(DURATION_HISTOGRAM_AGGREGATION));

    if (histogramAggregationResult.isPresent()) {
      for (MultiBucketsAggregation.Bucket durationBucket : histogramAggregationResult.get().getBuckets()) {
        final List<CompositeCommandResult.DistributedByResult> distributions = distributedByPart.retrieveResult(
          response,
          durationBucket.getAggregations(),
          context
        );
        durationHistogramData.add(CompositeCommandResult.GroupByResult.createGroupByResult(
          durationBucket.getKeyAsString(),
          distributions
        ));
      }
    }
    return durationHistogramData;
  }

  private Optional<AggregationBuilder> createLimitedGroupByScriptedDurationAggregation(
    final ExecutionContext<ProcessReportDataDto> context,
    final DistributedByPart<ProcessReportDataDto> distributedByPart,
    final Script durationCalculationScript,
    final MinMaxStatDto minMaxStats,
    final BiFunction<ComparisonOperator, Double, QueryBuilder> limitingFilterCreator) {

    if (minMaxStats.isEmpty()) {
      return Optional.empty();
    }

    final SingleReportConfigurationDto reportConfigurationDto = context.getReportData().getConfiguration();

    final CustomBucketDto customBucketDto = reportConfigurationDto.getCustomBucket();
    final double minValueInMillis = getMinValueInMillis(minMaxStats, customBucketDto);
    final double maxValueInMillis = minMaxStats.getMax();
    if (minValueInMillis > maxValueInMillis) {
      return Optional.empty();
    }

    final double intervalInMillis = getIntervalInMillis(minValueInMillis, maxValueInMillis, customBucketDto);

    final BoolQueryBuilder limitingFilter = boolQuery()
      .filter(limitingFilterCreator.apply(ComparisonOperator.GREATER_THAN_EQUALS, minValueInMillis));

    final HistogramAggregationBuilder histogramAggregation = generateHistogramFromScript(
      DURATION_HISTOGRAM_AGGREGATION,
      intervalInMillis,
      minValueInMillis,
      durationCalculationScript,
      maxValueInMillis,
      distributedByPart.createAggregations(context)
    );

    return Optional.of(wrapWithFilterLimitedParentAggregation(limitingFilter, histogramAggregation));
  }

  private double getIntervalInMillis(final double minValueInMillis,
                                     final double maxValueInMillis,
                                     final CustomBucketDto customBucketDto) {
    final double distance = maxValueInMillis - minValueInMillis;
    final double interval;
    if (customBucketDto.isActive()) {
      interval = customBucketDto.getBucketSizeInUnit(DEFAULT_UNIT).orElse(1.0D);
    } else if (distance <= AUTOMATIC_BUCKET_LIMIT) {
      interval = 1.0D;
    } else {
      // this is the minimal interval needed to ensure there are no more buckets than the limit
      final int minimalInterval = (int) Math.ceil(distance / AUTOMATIC_BUCKET_LIMIT);
      // as base 10 intervals are easier to read
      interval = roundUpToNearestPowerOfTen((double) minimalInterval).intValue();
    }
    return interval;
  }

  private double getMinValueInMillis(final MinMaxStatDto minMaxStats, final CustomBucketDto customBucketDto) {
    if (customBucketDto.isActive()) {
      return customBucketDto.getBaselineInUnit(DEFAULT_UNIT).orElse(0.0D);
    } else {
      return roundDownToNearestPowerOfTen(minMaxStats.getMin());
    }
  }

  private ScriptQueryBuilder createUserTaskLimitingFilterQuery(final ComparisonOperator filterOperator,
                                                               final UserTaskDurationTime userTaskDurationTime,
                                                               final double filterValueInMillis) {
    return createLimitingFilterQuery(
      filterOperator,
      (long) filterValueInMillis,
      FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName(),
      FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE,
      // user task duration calculations can be null (e.g. work time if the userTask hasn't been claimed)
      true
    );
  }

  private ScriptQueryBuilder createEventLimitingFilterQuery(final ComparisonOperator filterOperator,
                                                            final double filterValueInMillis) {
    return createLimitingFilterQuery(
      filterOperator,
      (long) filterValueInMillis,
      FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION,
      FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE,
      false
    );
  }

  private ScriptQueryBuilder createProcessInstanceLimitingFilterQuery(final ComparisonOperator filterOperator,
                                                                      final double filterValueInMillis) {
    return createLimitingFilterQuery(filterOperator, (long) filterValueInMillis, DURATION, START_DATE, false);
  }

  private ScriptQueryBuilder createLimitingFilterQuery(final ComparisonOperator filterOperator,
                                                       final long filterValueInMillis,
                                                       final String durationFieldName,
                                                       final String referenceDateFieldName,
                                                       final boolean includeNull) {
    return QueryBuilders.scriptQuery(
      DurationScriptUtil.getDurationFilterScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        durationFieldName,
        referenceDateFieldName,
        DurationFilterDataDto.builder()
          .operator(filterOperator)
          .unit(FILTER_UNIT)
          .value(filterValueInMillis)
          .includeNull(includeNull)
          .build()
      )
    );
  }

  private String[] getIndexNames(final ExecutionContext<ProcessReportDataDto> context) {
    return getProcessInstanceIndexAliasNames(context.getReportData());
  }
}
