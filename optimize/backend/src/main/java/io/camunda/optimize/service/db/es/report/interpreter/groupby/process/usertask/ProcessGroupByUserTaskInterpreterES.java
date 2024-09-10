/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.usertask;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_USER_TASK;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByUserTaskInterpreterES extends AbstractGroupByUserTaskInterpreterES {
  private static final String USER_TASK_ID_TERMS_AGGREGATION = "userTaskIds";

  private final ConfigurationService configurationService;
  @Getter private final DefinitionService definitionService;
  @Getter private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_USER_TASK);
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final TermsAggregationBuilder userTaskTermsAggregation =
        AggregationBuilders.terms(USER_TASK_ID_TERMS_AGGREGATION)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID);
    distributedByInterpreter
        .createAggregations(context, searchSourceBuilder.query())
        .forEach(userTaskTermsAggregation::subAggregation);
    return Collections.singletonList(
        createFilteredUserTaskAggregation(
            context, searchSourceBuilder.query(), userTaskTermsAggregation));
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    getFilteredUserTaskAggregation(response)
        .map(
            filteredFlowNodes ->
                (Terms) filteredFlowNodes.getAggregations().get(USER_TASK_ID_TERMS_AGGREGATION))
        .ifPresent(
            userTasksAggregation -> {
              getUserTasksAggregation(response)
                  .map(SingleBucketAggregation::getAggregations)
                  .ifPresent(
                      userTaskSubAggregations ->
                          distributedByInterpreter.enrichContextWithAllExpectedDistributedByKeys(
                              context, userTaskSubAggregations));

              final Map<String, String> userTaskNames = getUserTaskNames(context.getReportData());
              List<GroupByResult> groupedData = new ArrayList<>();
              for (Terms.Bucket b : userTasksAggregation.getBuckets()) {
                final String userTaskKey = b.getKeyAsString();
                if (userTaskNames.containsKey(userTaskKey)) {
                  final List<DistributedByResult> singleResult =
                      distributedByInterpreter.retrieveResult(
                          response, b.getAggregations(), context);
                  String label = userTaskNames.get(userTaskKey);
                  groupedData.add(
                      GroupByResult.createGroupByResult(userTaskKey, label, singleResult));
                  userTaskNames.remove(userTaskKey);
                }
              }

              addMissingGroupByResults(userTaskNames, groupedData, context);

              compositeCommandResult.setGroupBySorting(
                  context
                      .getReportConfiguration()
                      .getSorting()
                      .orElseGet(() -> new ReportSortingDto(null, SortOrder.ASC)));
              compositeCommandResult.setGroups(groupedData);
            });
  }

  private void addMissingGroupByResults(
      final Map<String, String> userTaskNames,
      final List<GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final boolean viewLevelFilterExists =
        context.getReportData().getFilter().stream()
            .anyMatch(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()));
    // If a view level filter exists, the data should not be enriched as the missing data has been
    // omitted by the filters
    if (!viewLevelFilterExists) {
      // If no view level filter exists, we enrich the user task data with user tasks that may not
      // have been executed, but should still show up in the result
      userTaskNames.forEach(
          (key, value) ->
              groupedData.add(
                  GroupByResult.createGroupByResult(
                      key, value, distributedByInterpreter.createEmptyResult(context))));
    }
  }

  private Map<String, String> getUserTaskNames(final ProcessReportDataDto reportData) {
    return definitionService.extractUserTaskIdAndNames(
        reportData.getDefinitions().stream()
            .map(
                definitionDto ->
                    definitionService.getDefinition(
                        DefinitionType.PROCESS,
                        definitionDto.getKey(),
                        definitionDto.getVersions(),
                        definitionDto.getTenantIds()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ProcessDefinitionOptimizeDto.class::cast)
            .collect(Collectors.toList()));
  }
}
