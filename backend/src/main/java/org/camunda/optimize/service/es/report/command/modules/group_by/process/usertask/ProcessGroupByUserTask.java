/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.usertask;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.UserTasksGroupByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
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
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByUserTask extends AbstractGroupByUserTask {
  private static final String USER_TASK_ID_TERMS_AGGREGATION = "tasks";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    return Collections.singletonList(createFilteredUserTaskAggregation(
      context,
      AggregationBuilders
        .terms(USER_TASK_ID_TERMS_AGGREGATION)
        .size(configurationService.getEsAggregationBucketLimit())
        .field(USER_TASKS + "." + USER_TASK_ACTIVITY_ID)
        .subAggregation(distributedByPart.createAggregation(context))
    ));
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    getFilteredUserTaskAggregation(response)
      .map(filteredFlowNodes -> (Terms) filteredFlowNodes.getAggregations().get(USER_TASK_ID_TERMS_AGGREGATION))
      .ifPresent(userTasksAggregation -> {
        getUserTasksAggregation(response)
          .map(SingleBucketAggregation::getAggregations)
          .ifPresent(userTaskSubAggregations -> distributedByPart.enrichContextWithAllExpectedDistributedByKeys(
            context,
            userTaskSubAggregations
          ));

        final Map<String, String> userTaskNames = getUserTaskNames(context.getReportData());
        List<GroupByResult> groupedData = new ArrayList<>();
        for (Terms.Bucket b : userTasksAggregation.getBuckets()) {
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
        compositeCommandResult.setIsComplete(userTasksAggregation.getSumOfOtherDocCounts() == 0L);
      });
  }

  private void addMissingGroupByResults(final Map<String, String> userTaskNames,
                                        final List<GroupByResult> groupedData,
                                        final ExecutionContext<ProcessReportDataDto> context) {
    final boolean viewLevelFilterExists = context.getReportData()
      .getFilter()
      .stream()
      .anyMatch(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()));
    // If a view level filter exists, the data should not be enriched as the missing data has been
    // omitted by the filters
    if (!viewLevelFilterExists) {
      // If no view level filter exists, we enrich the user task data with user tasks that may not have been executed,
      // but should still show up in the result (limited by ESBucketLimit)
      userTaskNames.keySet().forEach(userTaskKey -> {
        if (groupedData.size() < configurationService.getEsAggregationBucketLimit()) {
          GroupByResult emptyResult;
          if (distributedByPart instanceof ProcessDistributedByNone) {
            emptyResult = GroupByResult.createResultWithEmptyDistributedBy(userTaskKey);
          } else {
            // Add empty result for each missing bucket
            emptyResult = GroupByResult.createResultWithEmptyDistributedBy(userTaskKey, context);
          }
          emptyResult.setLabel(userTaskNames.get(userTaskKey));
          groupedData.add(emptyResult);
        }
      });
    }
  }

  private Map<String, String> getUserTaskNames(final ProcessReportDataDto reportData) {
    return definitionService
      .getDefinition(
        DefinitionType.PROCESS,
        reportData.getDefinitionKey(),
        reportData.getDefinitionVersions(),
        reportData.getTenantIds()
      )
      .map(def -> ((ProcessDefinitionOptimizeDto) def).getUserTaskNames())
      .orElse(Collections.emptyMap());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new UserTasksGroupByDto());
  }

}
