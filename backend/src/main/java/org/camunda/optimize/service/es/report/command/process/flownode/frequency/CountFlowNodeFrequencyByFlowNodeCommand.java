/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.flownode.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.process.FlowNodeFrequencyGroupingCommand;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class CountFlowNodeFrequencyByFlowNodeCommand extends FlowNodeFrequencyGroupingCommand {

  private static final String MI_BODY = "multiInstanceBody";

  @Override
  protected SingleProcessMapReportResult evaluate() {
    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating count flow node frequency grouped by flow node report " +
        "for process definition key [{}] and versions [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersions()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(processReportData.getConfiguration().getFlowNodeExecutionState()))
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME).source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final ReportMapResultDto resultDto = mapToReportResult(response);
      return new SingleProcessMapReportResult(resultDto, reportDefinition);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate count flow node frequency grouped by flow node report " +
            "for process definition with key [%s] and versions [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersions()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  protected void sortResultData(final SingleProcessMapReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getConfiguration().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, evaluationResult)
    );
  }

  private AggregationBuilder createAggregation(FlowNodeExecutionState flowNodeExecutionState) {
    // @formatter:off
    return
      nested("events", "events")
        .subAggregation(
            filter(
            "filteredEvents",
              addExecutionStateFilter(
                 boolQuery()
                .mustNot(
                  termQuery(EVENTS + "." + ACTIVITY_TYPE, MI_BODY)
                ),
                flowNodeExecutionState,
                EVENTS + "." + ACTIVITY_END_DATE
              )
          )
          .subAggregation(AggregationBuilders
            .terms("activities")
            .size(configurationService.getEsAggregationBucketLimit())
            .field("events.activityId")
          )
        );
    // @formatter:on
  }

  private ReportMapResultDto mapToReportResult(final SearchResponse response) {
    final ReportMapResultDto resultDto = new ReportMapResultDto();

    final Aggregations aggregations = response.getAggregations();
    final Nested activities = aggregations.get("events");
    final Filter filteredActivities = activities.getAggregations().get("filteredEvents");
    final Terms activityTerms = filteredActivities.getAggregations().get("activities");

    final List<MapResultEntryDto> resultData = new ArrayList<>();
    for (Terms.Bucket b : activityTerms.getBuckets()) {
      resultData.add(new MapResultEntryDto(b.getKeyAsString(), b.getDocCount()));
    }

    resultDto.setData(resultData);
    resultDto.setIsComplete(activityTerms.getSumOfOtherDocCounts() == 0L);
    resultDto.setInstanceCount(response.getHits().getTotalHits());
    return resultDto;
  }

}
