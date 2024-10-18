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
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

  public ProcessGroupByFlowNodeInterpreterES(
      ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      ProcessViewInterpreterFacadeES viewInterpreter,
      ConfigurationService configurationService,
      DefinitionService definitionService) {
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
    this.definitionService = definitionService;
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
              final Map<String, String> flowNodeNames = getFlowNodeNames(context.getReportData());
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
              addMissingGroupByKeys(flowNodeNames, groupedData, context);
              removeHiddenModelElements(groupedData, context);
              compositeCommandResult.setGroups(groupedData);
            });
  }

  private void addMissingGroupByKeys(
      final Map<String, String> flowNodeNames,
      final List<GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final boolean viewLevelFilterExists =
        context.getReportData().getFilter().stream()
            .anyMatch(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()));
    // If a view level filter exists, the data should not be enriched as the missing data could been
    // omitted by the filters
    if (!viewLevelFilterExists) {
      // If no view level filter exists, we enrich data with flow nodes that haven't been executed,
      // but should still
      // show up in the result
      flowNodeNames.forEach(
          (key, value) ->
              groupedData.add(
                  GroupByResult.createGroupByResult(
                      key, value, distributedByInterpreter.createEmptyResult(context))));
    }
  }

  private void removeHiddenModelElements(
      final List<GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (context.getHiddenFlowNodeIds() != null && !context.getHiddenFlowNodeIds().isEmpty()) {
      groupedData.removeIf(
          dataPoint -> context.getHiddenFlowNodeIds().contains(dataPoint.getKey()));
    }
  }

  private Map<String, String> getFlowNodeNames(final ProcessReportDataDto reportData) {
    return definitionService.extractFlowNodeIdAndNames(
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

  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }

  public DefinitionService getDefinitionService() {
    return this.definitionService;
  }
}
