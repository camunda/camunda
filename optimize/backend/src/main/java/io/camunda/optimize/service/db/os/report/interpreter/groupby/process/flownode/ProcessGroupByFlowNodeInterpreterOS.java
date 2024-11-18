/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.termAggregation;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper;
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
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByFlowNodeInterpreterOS extends AbstractGroupByFlowNodeInterpreterOS {
  private static final String NESTED_EVENTS_AGGREGATION = "nestedEvents";

  private final ConfigurationService configurationService;
  private final ProcessGroupByFlowNodeInterpreterHelper helper;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByFlowNodeInterpreterOS(
      final ConfigurationService configurationService,
      final ProcessGroupByFlowNodeInterpreterHelper helper,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    super();
    this.configurationService = configurationService;
    this.helper = helper;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_FLOW_NODE);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final int size = configurationService.getOpenSearchConfiguration().getAggregationBucketLimit();
    final Aggregation aggregation =
        new Aggregation.Builder()
            .terms(termAggregation(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID, size))
            .aggregations(distributedByInterpreter.createAggregations(context, query))
            .build();
    return createFilteredFlowNodeAggregation(
        context, Map.of(NESTED_EVENTS_AGGREGATION, aggregation));
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
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
                final String flowNodeKey = flowNodeBucket.key();
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
  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
