/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.usertask;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_USER_TASK;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.DistributedByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.ViewInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.groupby.usertask.ProcessGroupByUserTaskInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.SingleBucketAggregateBase;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByUserTaskInterpreterOS extends AbstractGroupByUserTaskInterpreterOS {

  private static final String USER_TASK_ID_TERMS_AGGREGATION = "userTaskIds";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final ProcessGroupByUserTaskInterpreterHelper helper;

  public ProcessGroupByUserTaskInterpreterOS(
      final ConfigurationService configurationService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final ProcessGroupByUserTaskInterpreterHelper helper) {
    this.configurationService = configurationService;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.helper = helper;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_USER_TASK);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregation aggregation =
        Aggregation.of(
            a -> {
              final Aggregation.Builder.ContainerBuilder terms =
                  a.terms(
                      t ->
                          t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
                              .size(
                                  configurationService
                                      .getOpenSearchConfiguration()
                                      .getAggregationBucketLimit()));
              distributedByInterpreter
                  .createAggregations(context, boolQuery)
                  .forEach(terms::aggregations);
              return terms;
            });

    return createFilteredUserTaskAggregation(
        context, boolQuery, USER_TASK_ID_TERMS_AGGREGATION, aggregation);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    getFilteredUserTaskAggregation(response)
        .map(
            filteredFlowNodes ->
                filteredFlowNodes.aggregations().get(USER_TASK_ID_TERMS_AGGREGATION).sterms())
        .ifPresent(
            userTasksAggregation -> {
              getUserTasksAggregation(response)
                  .map(SingleBucketAggregateBase::aggregations)
                  .ifPresent(
                      userTaskSubAggregations ->
                          distributedByInterpreter.enrichContextWithAllExpectedDistributedByKeys(
                              context, userTaskSubAggregations));

              final Map<String, String> userTaskNames =
                  getHelper().getUserTaskNames(context.getReportData());
              final List<GroupByResult> groupedData = new ArrayList<>();
              for (final StringTermsBucket b : userTasksAggregation.buckets().array()) {
                final String userTaskKey = b.key();
                if (userTaskNames.containsKey(userTaskKey)) {
                  final List<CompositeCommandResult.DistributedByResult> singleResult =
                      distributedByInterpreter.retrieveResult(response, b.aggregations(), context);
                  final String label = userTaskNames.get(userTaskKey);
                  groupedData.add(
                      GroupByResult.createGroupByResult(userTaskKey, label, singleResult));
                  userTaskNames.remove(userTaskKey);
                }
              }

              getHelper()
                  .addMissingGroupByResults(
                      userTaskNames, groupedData, context, distributedByInterpreter);
              getHelper().removeHiddenModelElements(groupedData, context);

              compositeCommandResult.setGroupBySorting(
                  context
                      .getReportConfiguration()
                      .getSorting()
                      .orElseGet(() -> new ReportSortingDto(null, SortOrder.ASC)));
              compositeCommandResult.setGroups(groupedData);
            });
  }

  @Override
  protected DistributedByInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan>
      getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  protected ViewInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan> getViewInterpreter() {
    return viewInterpreter;
  }

  @Override
  protected DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  public ProcessGroupByUserTaskInterpreterHelper getHelper() {
    return helper;
  }
}
