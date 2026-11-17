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
import io.camunda.optimize.service.util.BpmnModelUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProcessGroupByFlowNodeInterpreterHelper {
  private static final String AD_HOC_SUB_PROCESS_STRUCTURE_ATTRIBUTE = "adHocSubProcessStructure";

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

  /**
   * Resolves, from the report's process definition model, which flow nodes are ad-hoc subprocess
   * containers and which are their inner tool nodes. Used by the agent flow-node grouping to render
   * per-tool heat inside an expanded AI Agent (ad-hoc subprocess) while other agent nodes keep
   * their aggregated tool-call total. Ids that are themselves containers (nested ad-hoc
   * subprocesses) are excluded from the child set so nested containers are not tinted as tools.
   *
   * <p>Parsing the BPMN model is not free and the structure is needed by both the aggregation and
   * the result-mapping phase of a single report evaluation, so the result is memoized on the
   * (per-request) {@link ExecutionContext} to parse the model at most once per report.
   */
  public AdHocSubProcessStructure resolveAdHocSubProcessStructure(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getOrComputeAttribute(
        AD_HOC_SUB_PROCESS_STRUCTURE_ATTRIBUTE,
        () -> computeAdHocSubProcessStructure(context.getReportData()));
  }

  /**
   * Only the report's first definition is parsed: this grouping is exclusively paired with the
   * heatmap views built by {@code AgenticControlDashboardService}, which only ever render a single
   * concrete BPMN diagram (mirroring {@code ReportEvaluationHandler#populateHeatmapXml}, which
   * likewise reads only the first definition for the diagram XML). BPMN flow-node IDs are unique
   * only within a single model, not across definitions, so merging container/child IDs from more
   * than one model would let an unrelated flow node in one definition collide with an ad-hoc
   * subprocess container or tool ID in another, misclassifying it in every group's result.
   */
  private AdHocSubProcessStructure computeAdHocSubProcessStructure(
      final ProcessReportDataDto reportData) {
    final Set<String> containerIds = new HashSet<>();
    final Set<String> childIds = new HashSet<>();
    reportData.getDefinitions().stream().findFirst().stream()
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
        .map(ProcessDefinitionOptimizeDto::getBpmn20Xml)
        .filter(Objects::nonNull)
        .forEach(
            xml -> {
              final Map<String, Set<String>> childIdsByContainer =
                  BpmnModelUtil.extractAdHocSubProcessChildElementIds(xml);
              containerIds.addAll(childIdsByContainer.keySet());
              childIdsByContainer.values().forEach(childIds::addAll);
            });
    childIds.removeAll(containerIds);
    return new AdHocSubProcessStructure(containerIds, childIds);
  }

  /**
   * Populates the given (empty) distributed-by results with a frequency value, i.e. sets every view
   * measure to {@code docCount}. Shared by the Elasticsearch and OpenSearch agent flow-node
   * interpreters, which build the per-tool heat of an ad-hoc subprocess from the raw activation
   * counts of its inner tool nodes.
   *
   * <p>The measures being overwritten here come from the TOOL_CALLS view's {@code
   * createEmptyResult}, which tags each one with the report's configured aggregation type (AVERAGE
   * by default, see {@code SingleReportConfigurationDto}). That labeling is only meaningful for the
   * real avg/min/max/sum aggregation over the numeric tool-calls field; this value is a plain
   * activation count, so on paper the label is misleading. It is deliberately left untouched
   * anyway: {@code CompositeCommandResult#createMeasureMap} pre-seeds its output measures purely
   * from the report's configured aggregation types (not from what any view interpreter actually
   * computes), so every measure in this report — including this one — must keep exactly that
   * identifier or it silently drops into a second, mismatched measure. That previously surfaced as
   * a spurious aggregation-type selector in the report tile with an empty heatmap under the
   * "expected" identifier and the real data hidden under the mismatched one.
   */
  public List<DistributedByResult> toFrequencyResult(
      final List<DistributedByResult> emptyDistributedByResult, final long docCount) {
    emptyDistributedByResult.forEach(
        distributedByResult -> {
          final var viewResult = distributedByResult.getViewResult();
          if (viewResult != null && viewResult.getViewMeasures() != null) {
            viewResult.getViewMeasures().forEach(measure -> measure.setValue((double) docCount));
          }
        });
    return emptyDistributedByResult;
  }

  /**
   * Hybrid variant of {@link #mapFlowNodeBucketsToGroupByResults} for the agent flow-node grouping.
   * Agent-node buckets keep their aggregated value, except ad-hoc subprocess containers which are
   * dropped (so the large AI Agent box is not tinted). Inner tool nodes of ad-hoc subprocesses are
   * instead emitted from the flow-node-instance buckets, valued by their own activation counts, so
   * the diagram shows per-tool heat inside the expanded container. All remaining model flow nodes
   * are backfilled with empty results and hidden nodes are stripped, as in the standard mapping.
   */
  public <A, F> List<GroupByResult> mapAgentFlowNodeBucketsToGroupByResults(
      final List<A> agentBuckets,
      final Function<A, String> agentKeyExtractor,
      final Function<A, List<DistributedByResult>> agentResultExtractor,
      final List<F> innerToolBuckets,
      final Function<F, String> innerToolKeyExtractor,
      final Function<F, List<DistributedByResult>> innerToolResultExtractor,
      final AdHocSubProcessStructure adHocSubProcessStructure,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final List<DistributedByResult> emptyDistributedByResult) {
    final Map<String, String> flowNodeNames = getFlowNodeNames(context.getReportData());
    final List<GroupByResult> groupedData = new ArrayList<>();

    for (final A bucket : agentBuckets) {
      final String flowNodeKey = agentKeyExtractor.apply(bucket);
      if (adHocSubProcessStructure.containerIds().contains(flowNodeKey)) {
        // The ad-hoc subprocess container is represented by its inner tool nodes instead.
        continue;
      }
      if (flowNodeNames.containsKey(flowNodeKey)) {
        groupedData.add(
            GroupByResult.createGroupByResult(
                flowNodeKey, flowNodeNames.get(flowNodeKey), agentResultExtractor.apply(bucket)));
        flowNodeNames.remove(flowNodeKey);
      }
    }

    for (final F bucket : innerToolBuckets) {
      final String flowNodeKey = innerToolKeyExtractor.apply(bucket);
      if (!adHocSubProcessStructure.childIds().contains(flowNodeKey)) {
        continue;
      }
      if (flowNodeNames.containsKey(flowNodeKey)) {
        groupedData.add(
            GroupByResult.createGroupByResult(
                flowNodeKey,
                flowNodeNames.get(flowNodeKey),
                innerToolResultExtractor.apply(bucket)));
        flowNodeNames.remove(flowNodeKey);
      }
    }

    addMissingGroupByKeys(flowNodeNames, groupedData, context, emptyDistributedByResult);
    removeHiddenModelElements(groupedData, context);
    return groupedData;
  }

  /**
   * The ad-hoc subprocess container ids and the ids of their inner tool nodes for a report's
   * definition model(s).
   */
  public record AdHocSubProcessStructure(Set<String> containerIds, Set<String> childIds) {}
}
