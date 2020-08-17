/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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
import static org.camunda.optimize.service.util.RoundingUtil.roundUpToNearestPowerOfTen;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Component
@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByDuration extends GroupByPart<ProcessReportDataDto> {
  private static final String DURATION_HISTOGRAM_AGGREGATION = "durationHistogram";
  private static final int BUCKET_LIMIT = NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

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

    final int interval;
    final double minMaxDistance = minMaxStats.getMax() - minMaxStats.getMin();
    if (minMaxDistance > BUCKET_LIMIT) {
      // this is the minimal interval needed to ensure there are no more buckets than the limit
      final int minimalInterval = (int) Math.ceil(Math.max(minMaxDistance, 1) / BUCKET_LIMIT);
      // as the minimal interval is often not dividable by 10 but intervals that are a multiple of 10 are easier to read
      interval = roundUpToNearestPowerOfTen((double) minimalInterval).intValue();
    } else {
      interval = 1;
    }
    final HistogramAggregationBuilder rangeAgg = AggregationBuilders
      .histogram(DURATION_HISTOGRAM_AGGREGATION)
      .interval(interval)
      .script(getDurationScript())
      .extendedBounds(minMaxStats.getMin(), minMaxStats.getMax())
      .subAggregation(distributedByPart.createAggregation(context));

    return Collections.singletonList(rangeAgg);
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    if (response.getAggregations() == null) {
      // aggregations are null if there are no instances in the report
      return;
    }

    final List<GroupByResult> durationHistogramData = new ArrayList<>();
    final Histogram durationHistogram = response.getAggregations().get(DURATION_HISTOGRAM_AGGREGATION);
    for (MultiBucketsAggregation.Bucket durationBucket : durationHistogram.getBuckets()) {
      final List<DistributedByResult> distributions =
        distributedByPart.retrieveResult(response, durationBucket.getAggregations(), context);
      durationHistogramData.add(GroupByResult.createGroupByResult(durationBucket.getKeyAsString(), distributions));
    }

    compositeCommandResult.setKeyIsOfNumericType(true);
    compositeCommandResult.setGroups(durationHistogramData);
    compositeCommandResult.setIsComplete(true);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new DurationGroupByDto());
  }

  @Override
  public Optional<MinMaxStatDto> calculateNumberRangeForGroupByNumber(
    final ExecutionContext<ProcessReportDataDto> context,
    final BoolQueryBuilder baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(baseQuery));
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

}
