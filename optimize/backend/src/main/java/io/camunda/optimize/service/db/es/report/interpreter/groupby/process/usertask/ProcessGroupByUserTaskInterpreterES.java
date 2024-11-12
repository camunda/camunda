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

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.SingleBucketAggregateBase;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.groupby.usertask.ProcessGroupByUserTaskInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByUserTaskInterpreterES extends AbstractGroupByUserTaskInterpreterES {

  private static final String USER_TASK_ID_TERMS_AGGREGATION = "userTaskIds";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ProcessGroupByUserTaskInterpreterHelper helper;

  public ProcessGroupByUserTaskInterpreterES(
      final ConfigurationService configurationService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter,
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
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
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
                                      .getElasticSearchConfiguration()
                                      .getAggregationBucketLimit()));
              distributedByInterpreter
                  .createAggregations(context, boolQuery)
                  .forEach((k, v) -> terms.aggregations(k, v.build()));
              return terms;
            });

    return createFilteredUserTaskAggregation(
        context, boolQuery, USER_TASK_ID_TERMS_AGGREGATION, aggregation);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
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
                final String userTaskKey = b.key().stringValue();
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
  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }

  @Override
  public DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  public ProcessGroupByUserTaskInterpreterHelper getHelper() {
    return helper;
  }
}
