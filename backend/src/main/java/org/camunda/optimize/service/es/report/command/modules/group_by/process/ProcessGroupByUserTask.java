/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.UserTasksGroupByDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_END_DATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByUserTask extends GroupByPart<ProcessReportDataDto> {

  private static final String USER_TASK_ID_TERMS_AGGREGATION = "tasks";
  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final FlowNodeExecutionState flowNodeExecutionState = context.getReportConfiguration().getFlowNodeExecutionState();
    final NestedAggregationBuilder groupByAssigneeAggregation = nested(USER_TASKS, USER_TASKS_AGGREGATION)
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
              .terms(USER_TASK_ID_TERMS_AGGREGATION)
              .size(configurationService.getEsAggregationBucketLimit())
              .field(USER_TASKS + "." + USER_TASK_ACTIVITY_ID)
              .subAggregation(distributedByPart.createAggregation(context))
          )
      )
      // sibling aggregation for distributedByPart for retrieval of all keys that
      // should be present in distributedBy result
      .subAggregation(distributedByPart.createAggregation(context));
    return Collections.singletonList(groupByAssigneeAggregation);
  }

  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    final Aggregations aggregations = response.getAggregations();
    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byTaskIdAggregation = filteredUserTasks.getAggregations().get(USER_TASK_ID_TERMS_AGGREGATION);

    distributedByPart.enrichContextWithAllExpectedDistributedByKeys(
      context,
      userTasks.getAggregations()
    );

    final Map<String, String> userTaskNames = getUserTaskNames(context.getReportData());
    List<GroupByResult> groupedData = new ArrayList<>();
    for (Terms.Bucket b : byTaskIdAggregation.getBuckets()) {
      final String userTaskKey = b.getKeyAsString();
      if (userTaskNames.containsKey(userTaskKey)) {
        final List<DistributedByResult> singleResult =
          distributedByPart.retrieveResult(response, b.getAggregations(), context);
        String label = userTaskNames.get(userTaskKey);
        groupedData.add(GroupByResult.createGroupByResult(userTaskKey, label, singleResult));
        userTaskNames.remove(userTaskKey);
      }
    }

    addMissingGroupByResults(userTaskNames, groupedData, context);

    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(byTaskIdAggregation.getSumOfOtherDocCounts() == 0L);
  }

  private void addMissingGroupByResults(final Map<String, String> userTaskNames,
                                        final List<GroupByResult> groupedData,
                                        final ExecutionContext<ProcessReportDataDto> context) {
    // enrich data user tasks that haven't been executed, but should still show up in the result (limited by
    // ESBucketLimit)
    final int bucketLimit = configurationService.getEsAggregationBucketLimit();
    userTaskNames.keySet().forEach(userTaskKey -> {
      if (groupedData.size() < bucketLimit) {
        // Add empty result for each missing bucket
        GroupByResult emptyResult = GroupByResult.createResultWithEmptyDistributedBy(userTaskKey, context);
        emptyResult.setLabel(userTaskNames.get(userTaskKey));
        groupedData.add(emptyResult);
      }
    });
  }

  private Map<String, String> getUserTaskNames(final ProcessReportDataDto reportData) {
    return processDefinitionReader
      .getProcessDefinitionFromFirstTenantIfAvailable(
        reportData.getDefinitionKey(), reportData.getDefinitionVersions(), reportData.getTenantIds()
      )
      .map(ProcessDefinitionOptimizeDto::getUserTaskNames)
      .orElse(Collections.emptyMap());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new UserTasksGroupByDto());
  }

}
