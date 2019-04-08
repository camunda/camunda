/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date;

import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.service.es.report.command.AutomaticGroupByDateCommand;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

public abstract class AbstractProcessInstanceDurationGroupByStartDateCommand
  extends ProcessReportCommand<SingleProcessMapDurationReportResult> implements AutomaticGroupByDateCommand {

  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  @Override
  public IntervalAggregationService getIntervalAggregationService() {
    return intervalAggregationService;
  }

  @Override
  protected SingleProcessMapDurationReportResult evaluate() throws OptimizeException {

    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating process instance duration grouped by start date report " +
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

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate process instance duration grouped by start date report " +
            "for process definition key [%s] and version [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    ProcessDurationReportMapResultDto mapResultDto = new ProcessDurationReportMapResultDto();
    mapResultDto.setData(processAggregations(response.getAggregations()));
    mapResultDto.setProcessInstanceCount(response.getHits().getTotalHits());
    return new SingleProcessMapDurationReportResult(mapResultDto, reportDefinition);
  }

  @Override
  public BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto reportDataDto) {
    return super.setupBaseQuery(reportDataDto);
  }

  @Override
  protected void sortResultData(final SingleProcessMapDurationReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getParameters().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, evaluationResult)
    );
  }

  private AggregationBuilder createAggregation(GroupByDateUnit unit, QueryBuilder query) throws OptimizeException {
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      return addOperationsAggregation(createAutomaticIntervalAggregation(query));
    }
    DateHistogramInterval interval = intervalAggregationService.getDateHistogramInterval(unit);
    DateHistogramAggregationBuilder dateHistogrammAggregation = AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .field(START_DATE)
      .order(BucketOrder.key(false))
      .dateHistogramInterval(interval)
      .timeZone(DateTimeZone.getDefault());
    return addOperationsAggregation(dateHistogrammAggregation);
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

  private Map<String, AggregationResultDto> processAggregations(Aggregations aggregations) {
    if (!aggregations.getAsMap().containsKey(DATE_HISTOGRAM_AGGREGATION)) {
      return processAutomaticIntervalAggregations(aggregations);
    }
    Histogram agg = aggregations.get(DATE_HISTOGRAM_AGGREGATION);
    Map<String, AggregationResultDto> result = new LinkedHashMap<>();
    // For each entry
    for (Histogram.Bucket entry : agg.getBuckets()) {
      DateTime key = (DateTime) entry.getKey();    // Key
      String formattedDate = key.withZone(DateTimeZone.getDefault()).toString(OPTIMIZE_DATE_FORMAT);

      AggregationResultDto operationResult = processAggregationOperation(entry.getAggregations());
      result.put(formattedDate, operationResult);
    }
    return result;
  }

  private Map<String, AggregationResultDto> processAutomaticIntervalAggregations(Aggregations aggregations) {
    return intervalAggregationService.mapIntervalAggregationsToKeyBucketMap(aggregations)
      .entrySet()
      .stream()
      .collect(
        Collectors.toMap(
          Map.Entry::getKey,
          e -> processAggregationOperation(e.getValue().getAggregations()),
          (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
          },
          LinkedHashMap::new
        )
      );
  }

  private AggregationBuilder addOperationsAggregation(AggregationBuilder aggregationBuilder) {
    createOperationsAggregations()
      .forEach(aggregationBuilder::subAggregation);
    return aggregationBuilder;
  }

  protected abstract AggregationResultDto processAggregationOperation(Aggregations aggs);

  protected abstract List<AggregationBuilder> createOperationsAggregations();


}
