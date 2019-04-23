/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.AutomaticGroupByDateCommand;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.es.report.command.process.util.ProcessInstanceQueryUtil;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
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

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.createProcessStartDateHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.getExtendedBoundsFromDateFilters;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.limitFiltersToMaxBucketsForGroupByUnit;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.isResultComplete;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class CountProcessInstanceFrequencyByStartDateCommand extends ProcessReportCommand<SingleProcessMapReportResult>
  implements AutomaticGroupByDateCommand {

  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  @Override
  public IntervalAggregationService getIntervalAggregationService() {
    return intervalAggregationService;
  }

  @Override
  protected SingleProcessMapReportResult evaluate() throws OptimizeException {

    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating count process instance frequency grouped by start date report " +
        "for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);

    StartDateGroupByValueDto groupByStartDate = ((StartDateGroupByDto) processReportData.getGroupBy()).getValue();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(groupByStartDate.getUnit(), query))
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .types(PROC_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final ProcessCountReportMapResultDto mapResultDto = mapToReportResult(response);
      return new SingleProcessMapReportResult(mapResultDto, reportDefinition);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate count process instance frequency grouped by start date report " +
            "for process definition with key [%s] and version [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  public BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto reportDataDto) {
    return super.setupBaseQuery(reportDataDto);
  }

  @Override
  protected void sortResultData(final SingleProcessMapReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getParameters().getSorting().ifPresent(
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
      .field(START_DATE)
      .dateHistogramInterval(interval)
      .timeZone(DateTimeZone.getDefault());

    final ProcessReportDataDto reportData = getReportData();

    final List<DateFilterDataDto> startFilterDataDtos = queryFilterEnhancer.extractFilters(
      reportData.getFilter(), StartDateFilterDto.class
    );
    final BoolQueryBuilder limitFilterQuery;
    if (!startFilterDataDtos.isEmpty()) {
      final List<DateFilterDataDto> limitedFilters = limitFiltersToMaxBucketsForGroupByUnit(
        startFilterDataDtos, unit, configurationService.getEsAggregationBucketLimit()
      );

      getExtendedBoundsFromDateFilters(
        limitedFilters,
        DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat())
      ).ifPresent(dateHistogramAggregation::extendedBounds);

      limitFilterQuery = boolQuery();
      queryFilterEnhancer.getStartDateQueryFilterService().addFilters(limitFilterQuery, limitedFilters);
    } else {
      limitFilterQuery = createProcessStartDateHistogramBucketLimitingFilterFor(
        reportData.getFilter(),
        unit,
        configurationService.getEsAggregationBucketLimit(),
        ProcessInstanceQueryUtil.getLatestStartDate(query, esClient).orElse(null),
        queryFilterEnhancer
      );
    }

    return wrapWithFilterLimitedParentAggregation(limitFilterQuery, dateHistogramAggregation);
  }

  private AggregationBuilder createAutomaticIntervalAggregation(QueryBuilder query) throws OptimizeException {

    Optional<AggregationBuilder> automaticIntervalAggregation =
      intervalAggregationService.createIntervalAggregation(
        dateIntervalRange,
        query,
        PROC_INSTANCE_TYPE,
        START_DATE
      );

    if (automaticIntervalAggregation.isPresent()) {
      return automaticIntervalAggregation.get();
    } else {
      return createAggregation(GroupByDateUnit.MONTH, query);
    }
  }

  private ProcessCountReportMapResultDto mapToReportResult(final SearchResponse response) {
    final ProcessCountReportMapResultDto resultDto = new ProcessCountReportMapResultDto();
    resultDto.setData(processAggregations(response.getAggregations()));
    resultDto.setProcessInstanceCount(response.getHits().getTotalHits());
    resultDto.setIsComplete(isResultComplete(response));
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
