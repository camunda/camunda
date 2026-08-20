/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.distributedby.process.model;

import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;
import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.ViewInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class ProcessDistributedByModelElementInterpreterHelper {
  private final DefinitionService definitionService;

  public ProcessDistributedByModelElementInterpreterHelper(
      final DefinitionService definitionService) {
    this.definitionService = definitionService;
  }

  public List<DistributedByResult> missingDistributions(
      final Map<String, FlowNodeDataDto> modelElementNames,
      final ViewInterpreter<ProcessReportDataDto, ProcessExecutionPlan> viewInterpreter,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Set<String> excludedFlowNodes =
        getExcludedFlowNodes(context.getReportData(), modelElementNames);
    // Only enrich distrBy buckets with flowNodes not excluded by executedFlowNode- or
    // identityFilters
    return modelElementNames.keySet().stream()
        .filter(key -> !excludedFlowNodes.contains(key))
        .map(
            key ->
                createDistributedByResult(
                    key,
                    modelElementNames.get(key).getName(),
                    viewInterpreter.createEmptyResult(context)))
        .toList();
  }

  public Map<String, FlowNodeDataDto> getModelElementData(
      final ProcessReportDataDto reportData,
      final Function<DefinitionOptimizeResponseDto, Map<String, FlowNodeDataDto>>
          modelElementDataExtractor) {
    return reportData.getDefinitions().stream()
        .map(
            definitionDto ->
                definitionService.getDefinition(
                    DefinitionType.PROCESS,
                    definitionDto.getKey(),
                    definitionDto.getVersions(),
                    definitionDto.getTenantIds()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(modelElementDataExtractor)
        .map(Map::entrySet)
        .flatMap(Collection::stream)
        // can't use Collectors.toMap as value can be null, see
        // https://bugs.openjdk.java.net/browse/JDK-8148463
        .collect(
            HashMap::new,
            (map, entry) -> map.put(entry.getKey(), entry.getValue()),
            HashMap::putAll);
  }

  private Set<String> getExcludedFlowNodes(
      final ProcessReportDataDto reportData, final Map<String, FlowNodeDataDto> modelElementNames) {
    final Set<String> excludedFlowNodes =
        reportData.getFilter().stream()
            .filter(
                filter ->
                    filter instanceof ExecutedFlowNodeFilterDto
                        && FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
            .map(ExecutedFlowNodeFilterDto.class::cast)
            .map(ExecutedFlowNodeFilterDto::getData)
            .flatMap(
                data ->
                    switch (data.getOperator()) {
                      case IN ->
                          modelElementNames.keySet().stream()
                              .filter(name -> !data.getValues().contains(name));
                      case NOT_IN -> data.getValues().stream();
                    })
            .collect(toSet());

    if (containsIdentityFilters(reportData)) {
      // Exclude all FlowNodes which are not of type userTask if any identityFilters are applied
      excludedFlowNodes.addAll(
          modelElementNames.values().stream()
              .filter(flowNode -> !FLOW_NODE_TYPE_USER_TASK.equalsIgnoreCase(flowNode.getType()))
              .map(FlowNodeDataDto::getId)
              .collect(toSet()));
    }
    return excludedFlowNodes;
  }

  private boolean containsIdentityFilters(final ProcessReportDataDto reportData) {
    return reportData.getFilter().stream()
        .anyMatch(
            filter ->
                filter instanceof AssigneeFilterDto || filter instanceof CandidateGroupFilterDto);
  }
}
