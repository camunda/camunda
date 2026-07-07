/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.groupby.flownode;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProcessGroupByFlowNodeInterpreterHelper {
  private final DefinitionService definitionService;

  public ProcessGroupByFlowNodeInterpreterHelper(final DefinitionService definitionService) {
    this.definitionService = definitionService;
  }

  public void addMissingGroupByKeys(
      final Map<String, String> flowNodeNames,
      final List<GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final List<DistributedByResult> emptyDistributedByResult) {
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
                  GroupByResult.createGroupByResult(key, value, emptyDistributedByResult)));
    }
  }

  public void removeHiddenModelElements(
      final List<GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (context.getHiddenFlowNodeIds() != null) {
      groupedData.removeIf(
          dataPoint -> context.getHiddenFlowNodeIds().contains(dataPoint.getKey()));
    }
  }

  /**
   * Assembles group-by results from already-located flow-node terms buckets. Shared by the standard
   * flow-node group-by (buckets read from the flowNodeInstances events) and the agent flow-node
   * group-by (buckets read from the nested agentInstances path): both differ only in how the terms
   * buckets are located, then run this identical map-known-nodes / backfill-missing / strip-hidden
   * pipeline. Kept DB-agnostic via callbacks so the Elasticsearch and OpenSearch bucket and
   * response types stay out of this helper.
   */
  public <B> List<GroupByResult> mapFlowNodeBucketsToGroupByResults(
      final List<B> buckets,
      final Function<B, String> flowNodeKeyExtractor,
      final Function<B, List<DistributedByResult>> distributedByResultExtractor,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final List<DistributedByResult> emptyDistributedByResult) {
    final Map<String, String> flowNodeNames = getFlowNodeNames(context.getReportData());
    final List<GroupByResult> groupedData = new ArrayList<>();
    for (final B bucket : buckets) {
      final String flowNodeKey = flowNodeKeyExtractor.apply(bucket);
      if (flowNodeNames.containsKey(flowNodeKey)) {
        final List<DistributedByResult> singleResult = distributedByResultExtractor.apply(bucket);
        groupedData.add(
            GroupByResult.createGroupByResult(
                flowNodeKey, flowNodeNames.get(flowNodeKey), singleResult));
        flowNodeNames.remove(flowNodeKey);
      }
    }
    addMissingGroupByKeys(flowNodeNames, groupedData, context, emptyDistributedByResult);
    removeHiddenModelElements(groupedData, context);
    return groupedData;
  }

  public Map<String, String> getFlowNodeNames(final ProcessReportDataDto reportData) {
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
}
