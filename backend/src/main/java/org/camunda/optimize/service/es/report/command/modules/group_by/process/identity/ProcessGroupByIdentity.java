/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.identity;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_END_DATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@RequiredArgsConstructor
public abstract class ProcessGroupByIdentity extends GroupByPart<ProcessReportDataDto> {

  protected static final String GROUP_BY_IDENTITY_TERMS_AGGREGATION = "identities";
  protected static final String USER_TASKS_AGGREGATION = "userTasks";
  protected static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";
  // temporary GROUP_BY_IDENTITY_MISSING_KEY to ensure no overlap between this label and userTask names
  private static final String GROUP_BY_IDENTITY_MISSING_KEY = "unassignedUserTasks___";

  protected final ConfigurationService configurationService;
  protected final LocalizationService localizationService;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final FlowNodeExecutionState flowNodeExecutionState = context.getReportConfiguration().getFlowNodeExecutionState();
    final NestedAggregationBuilder groupByIdentityAggregation = nested(USER_TASKS, USER_TASKS_AGGREGATION)
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
              .terms(GROUP_BY_IDENTITY_TERMS_AGGREGATION)
              .size(configurationService.getEsAggregationBucketLimit())
              .order(BucketOrder.key(true))
              .field(USER_TASKS + "." + getIdentityField())
              .missing(GROUP_BY_IDENTITY_MISSING_KEY)
              .subAggregation(distributedByPart.createAggregation(context)))
      );

    return Collections.singletonList(groupByIdentityAggregation);
  }

  protected abstract String getIdentityField();

  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {

    final Aggregations aggregations = response.getAggregations();
    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byIdentityAggregation = filteredUserTasks.getAggregations().get(GROUP_BY_IDENTITY_TERMS_AGGREGATION);

    final List<GroupByResult> groupedData =
      getGroupedDataFromAggregations(response, filteredUserTasks, context);

    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(byIdentityAggregation.getSumOfOtherDocCounts() == 0L);
    compositeCommandResult.setSorting(
      context.getReportConfiguration()
        .getSorting()
        .orElseGet(() -> new SortingDto(SortingDto.SORT_BY_LABEL, SortOrder.ASC))
    );
  }

  private List<CompositeCommandResult.GroupByResult> getGroupedDataFromAggregations(final SearchResponse response,
                                                                                    final Filter filteredUserTasks,
                                                                                    final ExecutionContext<ProcessReportDataDto> context) {
    List<CompositeCommandResult.GroupByResult> groupByResults = new ArrayList<>();
    groupByResults.addAll(getByIdentityAggregationResults(response, filteredUserTasks, context));
    return groupByResults;
  }

  private List<GroupByResult> getByIdentityAggregationResults(final SearchResponse response,
                                                              final Filter filteredUserTasks,
                                                              final ExecutionContext<ProcessReportDataDto> context) {
    final Terms byIdentityAggregation = filteredUserTasks.getAggregations().get(GROUP_BY_IDENTITY_TERMS_AGGREGATION);
    final List<GroupByResult> groupedData = new ArrayList<>();
    for (Terms.Bucket identityBucket : byIdentityAggregation.getBuckets()) {
      final List<DistributedByResult> singleResult =
        distributedByPart.retrieveResult(response, identityBucket.getAggregations(), context);

      if (identityBucket.getKeyAsString().equals(GROUP_BY_IDENTITY_MISSING_KEY)) {
        // ensure missing identity bucket is excluded if its empty
        final boolean resultIsEmpty = singleResult.isEmpty()
          || singleResult.stream()
          .allMatch(
            result -> result.getViewResult().getNumber() == null || result.getViewResult().getNumber().equals(0L)
          );
        if (!resultIsEmpty) {
          groupedData.add(GroupByResult.createGroupByResult(
            localizationService.getDefaultLocaleMessageForMissingAssigneeLabel(),
            singleResult
          ));
        }
      } else {
        groupedData.add(GroupByResult.createGroupByResult(identityBucket.getKeyAsString(), singleResult));
      }
    }
    return groupedData;
  }
}
