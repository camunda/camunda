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
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
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
      );
    return Collections.singletonList(groupByAssigneeAggregation);
  }

  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {

    final Aggregations aggregations = response.getAggregations();
    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byTaskIdAggregation = filteredUserTasks.getAggregations().get(USER_TASK_ID_TERMS_AGGREGATION);

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

    final boolean hasDistributedByPartNone = distributedByPart instanceof ProcessDistributedByNone;

    groupedData = addMissingGroupByResults(userTaskNames, groupedData, hasDistributedByPartNone);

    if (!hasDistributedByPartNone) {
      groupedData = addMissingDistributedByResults(groupedData);
    }

    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(byTaskIdAggregation.getSumOfOtherDocCounts() == 0L);
  }

  private List<GroupByResult> addMissingGroupByResults(final Map<String, String> userTaskNames,
                                                       final List<GroupByResult> groupedData,
                                                       final boolean hasDistributedByPartNone) {
    // enrich data user tasks that haven't been executed, but should still show up in the result (limited by
    // ESBucketLimit)
    final int bucketLimit = configurationService.getEsAggregationBucketLimit();
    userTaskNames.keySet().forEach(userTaskKey -> {
      if (groupedData.size() < bucketLimit) {
        // If result is distributed by none, add empty distributed by.
        // Otherwise, prepare empty list to be filled with missing distributed by parts later
        GroupByResult emptyResult = hasDistributedByPartNone
          ? GroupByResult.createResultWithEmptyDistributedBy(userTaskKey)
          : GroupByResult.createResultWithNoDistributedBy(userTaskKey);
        emptyResult.setLabel(userTaskNames.get(userTaskKey));
        groupedData.add(emptyResult);
      }
    });
    return groupedData;
  }

  private List<GroupByResult> addMissingDistributedByResults(final List<GroupByResult> groupedData) {
    // Enrich data with empty distributed by results for all distributedByKeys that may not exist in some groupByResults
    Map<String, String> allDistributedByKeys = getDistributedByKeyLabelMap(groupedData);
    for (GroupByResult groupByResult : groupedData) {
      Set<String> presentDistributedByKeys = groupByResult.getDistributions()
        .stream()
        .map(distributed -> distributed.getKey())
        .collect(toSet());
      if (!presentDistributedByKeys.containsAll(allDistributedByKeys.keySet())) {
        // some distributions are missing, add empty distributions for each missing key
        Set<String> missingDistributedByKeys = new HashSet<>(allDistributedByKeys.keySet());
        missingDistributedByKeys.removeAll(presentDistributedByKeys);
        for (String key : missingDistributedByKeys) {
          DistributedByResult emptyDistributedResult =
            DistributedByResult.createResultWithEmptyValue(key, allDistributedByKeys.get(key));
          groupByResult.getDistributions().add(emptyDistributedResult);
        }
      }
    }
    return groupedData;
  }

  private Map<String, String> getDistributedByKeyLabelMap(final List<GroupByResult> groupedData) {
    Set<String> existingKeys = new HashSet<>();
    return groupedData.stream()
      .flatMap(groupByResult -> groupByResult.getDistributions().stream())
      .filter(distr -> existingKeys.add(distr.getKey()))
      .collect(toMap(distr -> distr.getKey(), distr -> distr.getLabel()));
  }

  private Map<String, String> getUserTaskNames(final ProcessReportDataDto reportData) {
    return processDefinitionReader.getProcessDefinitionFromFirstTenantIfAvailable(reportData)
      .orElse(new ProcessDefinitionOptimizeDto())
      .getUserTaskNames();
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new FlowNodesGroupByDto());
  }

}
