/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.user_task.frequency.groupby;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
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

import static org.camunda.optimize.service.es.report.command.util.FlowNodeExecutionStateAggregationUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASKS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_END_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class UserTaskFrequencyByCandidateGroupCommand extends ProcessReportCommand<SingleProcessMapReportResult> {

  private static final String USER_TASK_CANDIDATE_GROUP_TERMS_AGGREGATION = "candidateGroups";
  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";

  @Override
  protected SingleProcessMapReportResult evaluate() {
    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating user task frequency grouped by candidate group report for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    final BoolQueryBuilder query = setupBaseQuery(processReportData);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(processReportData.getConfiguration().getFlowNodeExecutionState()))
      .size(0);
    final SearchRequest searchRequest = new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final ProcessCountReportMapResultDto resultDto = mapToReportResult(response);
      return new SingleProcessMapReportResult(resultDto, reportDefinition);
    } catch (IOException e) {
      final String reason = String.format(
        "Could not evaluate user task frequency grouped by candidate group report " +
          "for process definition key [%s] and version [%s]",
        processReportData.getProcessDefinitionKey(),
        processReportData.getProcessDefinitionVersion()
      );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  protected void sortResultData(final SingleProcessMapReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getParameters().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, evaluationResult)
    );
  }

  private AggregationBuilder createAggregation(FlowNodeExecutionState flowNodeExecutionState) {
    return nested(USER_TASKS, USER_TASKS_AGGREGATION)
      .subAggregation(
        filter(
          FILTERED_USER_TASKS_AGGREGATION,
          addExecutionStateFilter(
            boolQuery(),
            flowNodeExecutionState,
            USER_TASKS + "." + USER_TASK_END_DATE
          )
        )
          .subAggregation(
            AggregationBuilders
              .terms(USER_TASK_CANDIDATE_GROUP_TERMS_AGGREGATION)
              .size(configurationService.getEsAggregationBucketLimit())
              .field(USER_TASKS + "." + USER_TASK_CANDIDATE_GROUPS)
          )
      );
  }

  private ProcessCountReportMapResultDto mapToReportResult(final SearchResponse response) {
    final ProcessCountReportMapResultDto resultDto = new ProcessCountReportMapResultDto();

    final Aggregations aggregations = response.getAggregations();
    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byTaskIdAggregation = filteredUserTasks.getAggregations().get(
      USER_TASK_CANDIDATE_GROUP_TERMS_AGGREGATION);

    final List<MapResultEntryDto<Long>> resultData = new ArrayList<>();
    for (Terms.Bucket b : byTaskIdAggregation.getBuckets()) {
      resultData.add(new MapResultEntryDto<>(b.getKeyAsString(), b.getDocCount()));
    }

    resultDto.setData(resultData);
    resultDto.setIsComplete(byTaskIdAggregation.getSumOfOtherDocCounts() == 0L);
    resultDto.setProcessInstanceCount(response.getHits().getTotalHits());

    return resultDto;
  }

}
