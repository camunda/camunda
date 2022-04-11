/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@RequiredArgsConstructor
public abstract class ProcessDistributedByModelElement extends ProcessDistributedByPart {

  private static final String MODEL_ELEMENT_ID_TERMS_AGGREGATION = "modelElement";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    final TermsAggregationBuilder modelElementTermsAggregation = AggregationBuilders
      .terms(MODEL_ELEMENT_ID_TERMS_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .order(BucketOrder.key(true))
      .field(getModelElementIdPath());
    viewPart.createAggregations(context).forEach(modelElementTermsAggregation::subAggregation);
    return Collections.singletonList(modelElementTermsAggregation);
  }

  @Override
  public List<DistributedByResult> retrieveResult(final SearchResponse response,
                                                  final Aggregations aggregations,
                                                  final ExecutionContext<ProcessReportDataDto> context) {
    final Terms byModelElementAggregation = aggregations.get(MODEL_ELEMENT_ID_TERMS_AGGREGATION);
    final Map<String, FlowNodeDataDto> modelElementData = getModelElementData(context.getReportData());
    final List<DistributedByResult> distributedByModelElements = new ArrayList<>();
    for (Terms.Bucket modelElementBucket : byModelElementAggregation.getBuckets()) {
      final ViewResult viewResult = viewPart.retrieveResult(response, modelElementBucket.getAggregations(), context);
      final String modelElementKey = modelElementBucket.getKeyAsString();
      if (modelElementData.containsKey(modelElementKey)) {
        String label = modelElementData.get(modelElementKey).getName();
        distributedByModelElements.add(createDistributedByResult(modelElementKey, label, viewResult));
        modelElementData.remove(modelElementKey);
      }
    }
    addMissingDistributions(modelElementData, distributedByModelElements, context);
    return distributedByModelElements;
  }

  private void addMissingDistributions(final Map<String, FlowNodeDataDto> modelElementNames,
                                       final List<DistributedByResult> distributedByModelElements,
                                       final ExecutionContext<ProcessReportDataDto> context) {
    final Set<String> excludedFlowNodes = getExcludedFlowNodes(context.getReportData(), modelElementNames);
    // Only enrich distrBy buckets with flowNodes not excluded by executedFlowNode- or identityFilters
    modelElementNames.keySet()
      .stream()
      .filter(key -> !excludedFlowNodes.contains(key))
      .forEach(key -> distributedByModelElements.add(
        DistributedByResult.createDistributedByResult(
          key, modelElementNames.get(key).getName(), getViewPart().createEmptyResult(context)
        )));
  }

  private Map<String, FlowNodeDataDto> getModelElementData(final ProcessReportDataDto reportData) {
    return reportData.getDefinitions().stream()
      .map(definitionDto -> definitionService.getDefinition(
        DefinitionType.PROCESS, definitionDto.getKey(), definitionDto.getVersions(), definitionDto.getTenantIds()
      ))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(this::extractModelElementData)
      .map(Map::entrySet)
      .flatMap(Collection::stream)
      // can't use Collectors.toMap as value can be null, see https://bugs.openjdk.java.net/browse/JDK-8148463
      .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
  }

  private Set<String> getExcludedFlowNodes(final ProcessReportDataDto reportData,
                                           final Map<String, FlowNodeDataDto> modelElementNames) {
    Set<String> excludedFlowNodes = reportData.getFilter()
      .stream()
      .filter(filter -> filter instanceof ExecutedFlowNodeFilterDto
        && FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .map(ExecutedFlowNodeFilterDto.class::cast)
      .map(ExecutedFlowNodeFilterDto::getData)
      .filter(data -> NOT_IN == data.getOperator())
      .flatMap(data -> data.getValues().stream())
      .collect(toSet());

    if (containsIdentityFilters(reportData)) {
      // Exclude all FlowNodes which are not of type userTask if any identityFilters are applied
      excludedFlowNodes.addAll(modelElementNames.values().stream()
                                 .filter(flowNode -> !FLOW_NODE_TYPE_USER_TASK.equalsIgnoreCase(flowNode.getType()))
                                 .map(FlowNodeDataDto::getId)
                                 .collect(toSet()));
    }
    return excludedFlowNodes;
  }

  private boolean containsIdentityFilters(final ProcessReportDataDto reportData) {
    return reportData.getFilter()
      .stream()
      .anyMatch(filter -> filter instanceof AssigneeFilterDto || filter instanceof CandidateGroupFilterDto);
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(getDistributedBy());
  }

  protected abstract String getModelElementIdPath();

  protected abstract Map<String, FlowNodeDataDto> extractModelElementData(DefinitionOptimizeResponseDto def);

  protected abstract ProcessReportDistributedByDto getDistributedBy();

}
