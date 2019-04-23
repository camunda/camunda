/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.decision.DecisionReportCommand;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionMapReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
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
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.createHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.getExtendedBoundsFromDateFilters;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.limitFiltersToMaxBuckets;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.isResultComplete;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.EVALUATION_DATE_TIME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class CountDecisionFrequencyGroupByEvaluationDateTimeCommand
  extends DecisionReportCommand<SingleDecisionMapReportResult> {

  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  @Override
  protected SingleDecisionMapReportResult evaluate() throws OptimizeException {

    final DecisionReportDataDto reportData = getReportData();
    logger.debug(
      "Evaluating count decision instance frequency grouped by evaluation date report " +
        "for decision definition key [{}] and version [{}]",
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    DecisionGroupByEvaluationDateTimeValueDto groupBy =
      ((DecisionGroupByEvaluationDateTimeDto) reportData.getGroupBy())
        .getValue();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(groupBy.getUnit(), query))
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE))
        .types(DECISION_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final DecisionReportMapResultDto mapResultDto = mapToReportResult(response);
      return new SingleDecisionMapReportResult(mapResultDto, reportDefinition);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate count decision instance frequency grouped by evaluation date report " +
            "for decision definition with key [%s] and version [%s]",
          reportData.getDecisionDefinitionKey(),
          reportData.getDecisionDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  protected void sortResultData(final SingleDecisionMapReportResult evaluationResult) {
    ((DecisionReportDataDto) getReportData()).getParameters().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, evaluationResult)
    );
  }

  private AggregationBuilder createAggregation(GroupByDateUnit unit, QueryBuilder query) throws OptimizeException {
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      return createAutomaticIntervalAggregation(query);
    }

    final DateHistogramInterval interval = intervalAggregationService.getDateHistogramInterval(unit);
    final DateHistogramAggregationBuilder dateHistogramAggregation = AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(EVALUATION_DATE_TIME)
      .dateHistogramInterval(interval)
      .timeZone(DateTimeZone.getDefault());

    final DecisionReportDataDto reportData = getReportData();

    final List<DateFilterDataDto> dateFilterDataDtos = queryFilterEnhancer.extractFilters(
      reportData.getFilter(),
      EvaluationDateFilterDto.class
    );

    final List<DateFilterDataDto> limitedFilters = limitFiltersToMaxBuckets(
      dateFilterDataDtos,
      unit,
      configurationService.getEsAggregationBucketLimit(),
      false
    );

    getExtendedBoundsFromDateFilters(
      limitedFilters,
      DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat())
    )
      .ifPresent(dateHistogramAggregation::extendedBounds);

    final BoolQueryBuilder limitFilterQuery = createHistogramBucketLimitingFilterFor(
      queryFilterEnhancer.extractFilters(reportData.getFilter(), EvaluationDateFilterDto.class),
      unit,
      configurationService.getEsAggregationBucketLimit(),
      queryFilterEnhancer.getEvaluationDateQueryFilter()
    );

    return wrapWithFilterLimitedParentAggregation(limitFilterQuery, dateHistogramAggregation);
  }

  private AggregationBuilder createAutomaticIntervalAggregation(QueryBuilder query) throws OptimizeException {

    Optional<AggregationBuilder> automaticIntervalAggregation =
      intervalAggregationService.createIntervalAggregation(
        dateIntervalRange,
        query,
        DECISION_INSTANCE_TYPE,
        EVALUATION_DATE_TIME
      );

    if (automaticIntervalAggregation.isPresent()) {
      return automaticIntervalAggregation.get();
    } else {
      return createAggregation(GroupByDateUnit.MONTH, query);
    }
  }

  private DecisionReportMapResultDto mapToReportResult(final SearchResponse response) {
    final DecisionReportMapResultDto resultDto = new DecisionReportMapResultDto();
    resultDto.setData(processAggregations(response.getAggregations()));
    resultDto.setDecisionInstanceCount(response.getHits().getTotalHits());
    resultDto.setComplete(isResultComplete(response));
    return resultDto;
  }

  private List<MapResultEntryDto<Long>> processAggregations(Aggregations aggregations) {
    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    List<MapResultEntryDto<Long>> result = new ArrayList<>();
    if (unwrappedLimitedAggregations.isPresent()) {
      final Histogram agg = unwrappedLimitedAggregations.get().get(DATE_HISTOGRAM_AGGREGATION);

      for (Histogram.Bucket entry : agg.getBuckets()) {
        DateTime key = (DateTime) entry.getKey();
        long docCount = entry.getDocCount();
        String formattedDate = key.withZone(DateTimeZone.getDefault()).toString(OPTIMIZE_DATE_FORMAT);
        result.add(new MapResultEntryDto<>(formattedDate, docCount));
      }
    } else {
      result = processAutomaticIntervalAggregations(aggregations);
    }
    return result;
  }

  private List<MapResultEntryDto<Long>> processAutomaticIntervalAggregations(Aggregations aggregations) {
    return intervalAggregationService.mapIntervalAggregationsToKeyBucketMap(
      aggregations)
      .entrySet()
      .stream()
      .map(stringBucketEntry -> new MapResultEntryDto<>(
        stringBucketEntry.getKey(), stringBucketEntry.getValue().getDocCount()
      ))
      .collect(Collectors.toList());
  }


}
