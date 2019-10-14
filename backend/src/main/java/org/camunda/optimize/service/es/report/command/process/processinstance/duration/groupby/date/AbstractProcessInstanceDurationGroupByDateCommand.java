/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.AutomaticGroupByDateCommand;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
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

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.getExtendedBoundsFromDateFilters;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.limitFiltersToMaxBucketsForGroupByUnit;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.isResultComplete;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

public abstract class AbstractProcessInstanceDurationGroupByDateCommand
  extends ProcessReportCommand<SingleProcessMapReportResult>
  implements AutomaticGroupByDateCommand {

  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  protected AggregationStrategy aggregationStrategy;

  AbstractProcessInstanceDurationGroupByDateCommand(AggregationStrategy strategy) {
    aggregationStrategy = strategy;
  }

  @Override
  public IntervalAggregationService getIntervalAggregationService() {
    return intervalAggregationService;
  }

  protected abstract Long processAggregationOperation(Aggregations aggs);

  protected abstract AggregationBuilder createOperationsAggregation();

  @Override
  protected SingleProcessMapReportResult evaluate() throws OptimizeException {

    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating process instance duration grouped by [{}] report " +
        "for process definition key [{}] and versions [{}]",
      processReportData.getGroupBy().getType().toString(),
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersions()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);
    query.must(existsQuery(getDateField()));

    GroupByDateUnit groupByDateUnit = getGroupByDateUnit(processReportData);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(groupByDateUnit, query))
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final ReportMapResultDto mapResultDto = mapToReportResult(response);
      return new SingleProcessMapReportResult(mapResultDto, reportDefinition);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate process instance duration grouped by [%s] date report " +
            "for process definition key [%s] and versions [%s]",
          processReportData.getGroupBy().getType().toString(),
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersions()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

  }

  private GroupByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

  @Override
  public BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto reportDataDto) {
    return super.setupBaseQuery(reportDataDto);
  }

  @Override
  protected void sortResultData(final SingleProcessMapReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getConfiguration().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, evaluationResult)
    );
  }


  private AggregationBuilder createAggregation(final GroupByDateUnit unit, final QueryBuilder query)
    throws OptimizeException {
    String dateField = getDateField();
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      return addOperationsAggregation(createAutomaticIntervalAggregation(query, dateField));
    }

    DateHistogramInterval interval = intervalAggregationService.getDateHistogramInterval(unit);
    DateHistogramAggregationBuilder dateHistogramAggregation = AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .field(dateField)
      .order(BucketOrder.key(false))
      .dateHistogramInterval(interval)
      .timeZone(DateTimeZone.getDefault());

    final ProcessReportDataDto reportData = getReportData();

    final List<DateFilterDataDto> reportDateFilter = getReportDateFilter(reportData);

    final BoolQueryBuilder limitFilterQuery;
    if (!reportDateFilter.isEmpty()) {
      final List<DateFilterDataDto> limitedFilters = limitFiltersToMaxBucketsForGroupByUnit(
        reportDateFilter, unit, configurationService.getEsAggregationBucketLimit()
      );

      getExtendedBoundsFromDateFilters(
        limitedFilters,
        DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat())
      ).ifPresent(dateHistogramAggregation::extendedBounds);

      limitFilterQuery = boolQuery();
      addFiltersToQuery(limitFilterQuery, limitedFilters);
    } else {
      limitFilterQuery = createDefaultLimitingFilter(unit, query, reportData);
    }

    return wrapWithFilterLimitedParentAggregation(limitFilterQuery, addOperationsAggregation(dateHistogramAggregation));
  }

  protected abstract void addFiltersToQuery(final BoolQueryBuilder limitFilterQuery,
                                            final List<DateFilterDataDto> limitedFilters);

  protected abstract List<DateFilterDataDto> getReportDateFilter(final ProcessReportDataDto reportData);

  protected abstract BoolQueryBuilder createDefaultLimitingFilter(final GroupByDateUnit unit, final QueryBuilder query,
                                                                  final ProcessReportDataDto reportData);

  private AggregationBuilder createAutomaticIntervalAggregation(QueryBuilder query, String dateField) throws
                                                                                                      OptimizeException {

    Optional<AggregationBuilder> automaticIntervalAggregation =
      intervalAggregationService.createIntervalAggregation(
        dateIntervalRange,
        query,
        PROCESS_INSTANCE_INDEX_NAME,
        dateField
      );

    if (automaticIntervalAggregation.isPresent()) {
      return automaticIntervalAggregation.get();
    } else {
      return createAggregation(GroupByDateUnit.MONTH, query);
    }
  }

  private AggregationBuilder addOperationsAggregation(AggregationBuilder aggregationBuilder) {
    return aggregationBuilder.subAggregation(createOperationsAggregation());
  }

  private ReportMapResultDto mapToReportResult(final SearchResponse response) {
    final ReportMapResultDto resultDto = new ReportMapResultDto();
    resultDto.setData(processAggregations(response.getAggregations()));
    resultDto.setIsComplete(isResultComplete(response));
    resultDto.setInstanceCount(response.getHits().getTotalHits());
    return resultDto;
  }

  private List<MapResultEntryDto<Long>> processAggregations(Aggregations aggregations) {
    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    List<MapResultEntryDto<Long>> resultData = new ArrayList<>();
    if (unwrappedLimitedAggregations.isPresent()) {
      final Histogram agg = unwrappedLimitedAggregations.get().get(DATE_HISTOGRAM_AGGREGATION);
      for (Histogram.Bucket entry : agg.getBuckets()) {
        DateTime key = (DateTime) entry.getKey();
        String formattedDate = key.withZone(DateTimeZone.getDefault()).toString(OPTIMIZE_DATE_FORMAT);

        Long operationResult = processAggregationOperation(entry.getAggregations());
        resultData.add(new MapResultEntryDto<>(formattedDate, operationResult));
      }
    } else {
      resultData = processAutomaticIntervalAggregations(aggregations);
    }
    return resultData;
  }

  private List<MapResultEntryDto<Long>> processAutomaticIntervalAggregations(Aggregations aggregations) {
    return intervalAggregationService.mapIntervalAggregationsToKeyBucketMap(aggregations)
      .entrySet()
      .stream()
      .map(stringBucketEntry -> new MapResultEntryDto<>(
        stringBucketEntry.getKey(),
        processAggregationOperation(stringBucketEntry.getValue().getAggregations())
      ))
      .collect(Collectors.toList());
  }
}
