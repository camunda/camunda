/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.usertask;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createModelElementAggregationFilter;
import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.SingleBucketAggregateBase;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.groupby.usertask.ProcessGroupByUserTaskInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractGroupByUserTaskInterpreterES
    extends AbstractProcessGroupByInterpreterES {

  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FLOW_NODE_AGGREGATION = "flowNodes";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";

  protected abstract DefinitionService getDefinitionService();

  protected abstract ProcessGroupByUserTaskInterpreterHelper getHelper();

  protected Map<String, ContainerBuilder> createFilteredUserTaskAggregation(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQuery,
      final String name,
      final Aggregation subAggregation) {
    // sibling aggregation next to filtered userTask agg for distributedByPart for retrieval of all
    // keys that
    // should be present in distributedBy result via enrichContextWithAllExpectedDistributedByKeys

    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().nested(n -> n.path(FLOW_NODE_INSTANCES));
    builder.aggregations(
        USER_TASKS_AGGREGATION,
        Aggregation.of(
            a -> {
              final Aggregation.Builder.ContainerBuilder aggregations =
                  a.filter(f -> f.bool(createUserTaskFlowNodeTypeFilter().build()))
                      .aggregations(
                          FILTERED_USER_TASKS_AGGREGATION,
                          Aggregation.of(
                              aa ->
                                  aa.filter(
                                          f ->
                                              f.bool(
                                                  createModelElementAggregationFilter(
                                                          context.getReportData(),
                                                          context.getFilterContext(),
                                                          getDefinitionService())
                                                      .build()))
                                      .aggregations(name, subAggregation)));
              getDistributedByInterpreter()
                  .createAggregations(context, baseQuery)
                  .forEach((k, v) -> aggregations.aggregations(k, v.build()));
              return aggregations;
            }));
    return Map.of(FLOW_NODE_AGGREGATION, builder);
  }

  protected Optional<FilterAggregate> getFilteredUserTaskAggregation(
      final ResponseBody<?> response) {
    return getUserTasksAggregation(response)
        .map(SingleBucketAggregateBase::aggregations)
        .map(aggs -> aggs.get(FILTERED_USER_TASKS_AGGREGATION).filter());
  }

  protected Optional<FilterAggregate> getUserTasksAggregation(final ResponseBody<?> response) {
    return Optional.ofNullable(response.aggregations())
        .filter(f -> !f.isEmpty())
        .map(aggs -> aggs.get(FLOW_NODE_AGGREGATION).nested())
        .map(flowNodeAgg -> flowNodeAgg.aggregations().get(USER_TASKS_AGGREGATION).filter());
  }
}
