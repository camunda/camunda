/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.BucketUnit;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil;
import org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.ScriptQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import static org.camunda.optimize.service.util.RoundingUtil.roundDownToNearestPowerOfTen;
import static org.camunda.optimize.service.util.RoundingUtil.roundUpToNearestPowerOfTen;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Component
@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByDuration extends GroupByPart<ProcessReportDataDto> {
  private static final String DURATION_HISTOGRAM_AGGREGATION = "durationHistogram";
  private static final int AUTOMATIC_BUCKET_LIMIT = NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

  private final ConfigurationService configurationService;
  private final MinMaxStatsService minMaxStatsService;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final MinMaxStatDto minMaxStats = minMaxStatsService.getMinMaxNumberRangeForScriptedField(
      context, searchSourceBuilder.query(), PROCESS_INSTANCE_INDEX_NAME, getDurationScript()
    );
    if (minMaxStats.isEmpty()) {
      return Collections.emptyList();
    }

    final SingleReportConfigurationDto reportConfigurationDto = context.getReportData().getConfiguration();

    final CustomBucketDto customBucketDto = reportConfigurationDto.getCustomBucket();
    final double minValue = getMinValue(minMaxStats, customBucketDto);
    final double maxValue = minMaxStats.getMax();
    if (minValue > minMaxStats.getMax()) {
      return Collections.emptyList();
    }

    final double interval = getInterval(minValue, maxValue, customBucketDto);
    final double limitedMaxBound = limitMaxBound(minValue, maxValue, interval);

    final BoolQueryBuilder limitingFilter = QueryBuilders.boolQuery()
      .filter(createLimitingFilterQuery(FilterOperator.GREATER_THAN_EQUALS, minValue))
      .filter(createLimitingFilterQuery(FilterOperator.LESS_THAN_EQUALS, limitedMaxBound));

    final HistogramAggregationBuilder histogramAggregation = AggregationBuilders
      .histogram(DURATION_HISTOGRAM_AGGREGATION)
      .interval(interval)
      .offset(minValue)
      .script(getDurationScript())
      .extendedBounds(minValue, limitedMaxBound)
      .subAggregation(distributedByPart.createAggregation(context));

    return Collections.<AggregationBuilder>singletonList(
      FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation(limitingFilter, histogramAggregation)
    );
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    final Optional<Histogram> histogramAggregationResult = Optional.ofNullable(response.getAggregations())
      .flatMap(aggregations -> FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations(response.getAggregations()))
      .map(aggregations -> aggregations.get(DURATION_HISTOGRAM_AGGREGATION));

    if (histogramAggregationResult.isPresent()) {
      final List<GroupByResult> durationHistogramData = new ArrayList<>();
      for (MultiBucketsAggregation.Bucket durationBucket : histogramAggregationResult.get().getBuckets()) {
        final List<DistributedByResult> distributions =
          distributedByPart.retrieveResult(response, durationBucket.getAggregations(), context);
        durationHistogramData.add(GroupByResult.createGroupByResult(durationBucket.getKeyAsString(), distributions));
      }
      compositeCommandResult.setKeyIsOfNumericType(true);
      compositeCommandResult.setGroups(durationHistogramData);
    }

    compositeCommandResult.setIsComplete(FilterLimitedAggregationUtil.isResultComplete(response));
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new DurationGroupByDto());
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context,
                                                final BoolQueryBuilder baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(baseQuery));
  }

  private double limitMaxBound(final double minValue,
                               final double maxValue,
                               final double interval) {
    final double distance = maxValue - minValue;
    double limitedMaxBound = maxValue;
    if (distance / interval > getAggregationBucketLimit()) {
      // -1 as the bound is inclusive
      limitedMaxBound = minValue + interval * getAggregationBucketLimit() - 1;
    }

    return limitedMaxBound;
  }

  private double getInterval(final double minValue, final double maxValue, final CustomBucketDto customBucketDto) {
    final double distance = maxValue - minValue;
    final double interval;
    if (customBucketDto.isActive()) {
      interval = customBucketDto.getBucketSizeInUnit(BucketUnit.MILLISECOND).orElse(1.0D);
    } else if (distance <= AUTOMATIC_BUCKET_LIMIT) {
      interval = 1.0D;
    } else {
      // this is the minimal interval needed to ensure there are no more buckets than the limit
      final int minimalInterval = (int) Math.ceil(Math.max(distance, 1) / AUTOMATIC_BUCKET_LIMIT);
      // as base 10 intervals are easier to read
      interval = roundUpToNearestPowerOfTen((double) minimalInterval).intValue();
    }
    return interval;
  }

  private double getMinValue(final MinMaxStatDto minMaxStats, final CustomBucketDto customBucketDto) {
    if (customBucketDto.isActive()) {
      return customBucketDto.getBaselineInUnit(BucketUnit.MILLISECOND).orElse(0.0D);
    } else {
      return roundDownToNearestPowerOfTen(minMaxStats.getMin());
    }
  }

  private ScriptQueryBuilder createLimitingFilterQuery(final FilterOperator filterOperator, final double filterValue) {
    return QueryBuilders.scriptQuery(
      ExecutionStateAggregationUtil.getDurationFilterScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        ProcessInstanceIndex.DURATION,
        ProcessInstanceIndex.START_DATE,
        DurationFilterDataDto.builder()
          .operator(filterOperator)
          .unit(DurationFilterUnit.MILLIS)
          .value((long) filterValue)
          .build()
      )
    );
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(final QueryBuilder baseQuery) {
    return minMaxStatsService.getScriptedMinMaxStats(baseQuery, PROCESS_INSTANCE_INDEX_NAME, getDurationScript());
  }

  private Script getDurationScript() {
    return ExecutionStateAggregationUtil.getDurationScript(
      LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
      ProcessInstanceIndex.DURATION,
      ProcessInstanceIndex.START_DATE
    );
  }

  private int getAggregationBucketLimit() {
    return configurationService.getEsAggregationBucketLimit();
  }

}
