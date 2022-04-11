/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.usertask;

import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.UserTasksGroupByDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByUserTask extends AbstractGroupByUserTask {
  private static final String USER_TASK_ID_TERMS_AGGREGATION = "userTaskIds";

  private final ConfigurationService configurationService;

  public ProcessGroupByUserTask(final ConfigurationService configurationService,
                                final DefinitionService definitionService) {
    super(definitionService);
    this.configurationService = configurationService;
  }

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final TermsAggregationBuilder userTaskTermsAggregation = AggregationBuilders
      .terms(USER_TASK_ID_TERMS_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID);
    distributedByPart.createAggregations(context).forEach(userTaskTermsAggregation::subAggregation);
    return Collections.singletonList(createFilteredUserTaskAggregation(context, userTaskTermsAggregation));
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

        compositeCommandResult.setGroupBySorting(
          context.getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(null, SortOrder.ASC))
        );
        compositeCommandResult.setGroups(groupedData);
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
      // but should still show up in the result
      userTaskNames.forEach((key, value) -> groupedData.add(
        GroupByResult.createGroupByResult(key, value, distributedByPart.createEmptyResult(context))
      ));
    }
  }

  private Map<String, String> getUserTaskNames(final ProcessReportDataDto reportData) {
    return definitionService.extractUserTaskIdAndNames(
      reportData.getDefinitions().stream()
        .map(definitionDto -> definitionService.getDefinition(
          DefinitionType.PROCESS, definitionDto.getKey(), definitionDto.getVersions(), definitionDto.getTenantIds()
        ))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(ProcessDefinitionOptimizeDto.class::cast)
        .collect(Collectors.toList())
    );
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new UserTasksGroupByDto());
  }

}
