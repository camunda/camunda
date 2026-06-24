/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper;
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
public class ProcessGroupByFlowNodeInterpreterES extends AbstractGroupByFlowNodeInterpreterES {

  private static final String NESTED_EVENTS_AGGREGATION = "nestedEvents";
  final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final ProcessGroupByFlowNodeInterpreterHelper helper;

  public ProcessGroupByFlowNodeInterpreterES(
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final ConfigurationService configurationService,
      final DefinitionService definitionService,
      final ProcessGroupByFlowNodeInterpreterHelper helper) {
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
    this.definitionService = definitionService;
    this.helper = helper;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_FLOW_NODE);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.size(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit())
                        .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID));
    distributedByInterpreter
        .createAggregations(context, boolQuery)
        .forEach((k, v) -> builder.aggregations(k, v.build()));
    return createFilteredFlowNodeAggregation(context, Map.of(NESTED_EVENTS_AGGREGATION, builder));
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    getFilteredFlowNodesAggregation(response)
        .map(
            filteredFlowNodes ->
                filteredFlowNodes.aggregations().get(NESTED_EVENTS_AGGREGATION).sterms())
        .ifPresent(
            byFlowNodeIdAggregation -> {
              final Map<String, String> flowNodeNames =
                  helper.getFlowNodeNames(context.getReportData());
              final List<GroupByResult> groupedData = new ArrayList<>();
              for (final StringTermsBucket flowNodeBucket :
                  byFlowNodeIdAggregation.buckets().array()) {
                final String flowNodeKey = flowNodeBucket.key().stringValue();
                if (flowNodeNames.containsKey(flowNodeKey)) {
                  final List<CompositeCommandResult.DistributedByResult> singleResult =
                      distributedByInterpreter.retrieveResult(
                          response, flowNodeBucket.aggregations(), context);
                  final String label = flowNodeNames.get(flowNodeKey);
                  groupedData.add(
                      GroupByResult.createGroupByResult(flowNodeKey, label, singleResult));
                  flowNodeNames.remove(flowNodeKey);
                }
              }
              helper.addMissingGroupByKeys(
                  flowNodeNames,
                  groupedData,
                  context,
                  distributedByInterpreter.createEmptyResult(context));
              helper.removeHiddenModelElements(groupedData, context);
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
}
