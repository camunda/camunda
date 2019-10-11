/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ReportMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.process.FlowNodeDurationGroupingCommand;
import org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@AllArgsConstructor
public class FlowNodeDurationByFlowNodeCommand extends FlowNodeDurationGroupingCommand {

  private static final String MI_BODY = "multiInstanceBody";
  private static final String EVENTS_AGGREGATION = "events";
  private static final String FILTERED_EVENTS_AGGREGATION = "filteredEvents";
  private static final String ACTIVITY_ID_TERMS_AGGREGATION = "activities";

  protected AggregationStrategy aggregationStrategy;

  @Override
  protected SingleProcessMapReportResult evaluate() {
    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating flow node duration grouped by flow node report " +
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
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
        .types(PROCESS_INSTANCE_INDEX_NAME)
        .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final ReportMapResult resultDto = mapToReportResult(response);
      return new SingleProcessMapReportResult(resultDto, reportDefinition);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate flow node duration grouped by " +
            "flow node report for process definition key [%s] and versions [%s]",
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
    return
      nested(EVENTS, EVENTS_AGGREGATION)
        .subAggregation(
          filter(
            FILTERED_EVENTS_AGGREGATION,
            addExecutionStateFilter(
              boolQuery()
                .mustNot(
                  termQuery(EVENTS + "." + ACTIVITY_TYPE, MI_BODY)
                ),
              flowNodeExecutionState,
              EVENTS + "." + ACTIVITY_END_DATE
            )
          )
            .subAggregation(
              terms(ACTIVITY_ID_TERMS_AGGREGATION)
                .size(configurationService.getEsAggregationBucketLimit())
                .field(EVENTS + "." + ACTIVITY_ID)
                .subAggregation(
                  aggregationStrategy.getAggregationBuilder().script(getScriptedAggregationField())
                )
            )
        );
  }

  private Script getScriptedAggregationField() {
    return ExecutionStateAggregationUtil.getDurationAggregationScript(
      LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
      EVENTS + "." + ACTIVITY_DURATION,
      EVENTS + "." + ACTIVITY_START_DATE
    );
  }

  private ReportMapResult mapToReportResult(final SearchResponse response) {
    final ReportMapResult resultDto = new ReportMapResult();

    final Aggregations aggregations = response.getAggregations();
    final Nested activities = aggregations.get(EVENTS_AGGREGATION);
    final Filter filteredActivities = activities.getAggregations().get(FILTERED_EVENTS_AGGREGATION);
    final Terms activityIdTerms = filteredActivities.getAggregations().get(ACTIVITY_ID_TERMS_AGGREGATION);

    final List<MapResultEntryDto<Long>> resultData = new ArrayList<>();
    for (Terms.Bucket b : activityIdTerms.getBuckets()) {

      final long value = aggregationStrategy.getValue(b.getAggregations());
      resultData.add(new MapResultEntryDto<>(b.getKeyAsString(), value));
    }

    resultDto.setData(resultData);
    resultDto.setIsComplete(activityIdTerms.getSumOfOtherDocCounts() == 0L);
    resultDto.setInstanceCount(response.getHits().getTotalHits());
    return resultDto;
  }

}
