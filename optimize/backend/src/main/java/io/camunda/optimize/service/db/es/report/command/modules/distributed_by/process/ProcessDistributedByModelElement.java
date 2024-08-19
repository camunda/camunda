/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;
import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

public abstract class ProcessDistributedByModelElement extends ProcessDistributedByPart {

  private static final String MODEL_ELEMENT_ID_TERMS_AGGREGATION = "modelElement";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;

  public ProcessDistributedByModelElement(
      final ConfigurationService configurationService, final DefinitionService definitionService) {
    this.configurationService = configurationService;
    this.definitionService = definitionService;
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto> context) {
    final TermsAggregationBuilder modelElementTermsAggregation =
        AggregationBuilders.terms(MODEL_ELEMENT_ID_TERMS_AGGREGATION)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .order(BucketOrder.key(true))
            .field(getModelElementIdPath());
    viewPart.createAggregations(context).forEach(modelElementTermsAggregation::subAggregation);
    return Collections.singletonList(modelElementTermsAggregation);
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<ProcessReportDataDto> context) {
    final Terms byModelElementAggregation = aggregations.get(MODEL_ELEMENT_ID_TERMS_AGGREGATION);
    final Map<String, FlowNodeDataDto> modelElementData =
        getModelElementData(context.getReportData());
    final List<DistributedByResult> distributedByModelElements = new ArrayList<>();
    for (final Terms.Bucket modelElementBucket : byModelElementAggregation.getBuckets()) {
      final ViewResult viewResult =
          viewPart.retrieveResult(response, modelElementBucket.getAggregations(), context);
      final String modelElementKey = modelElementBucket.getKeyAsString();
      if (modelElementData.containsKey(modelElementKey)) {
        final String label = modelElementData.get(modelElementKey).getName();
        distributedByModelElements.add(
            createDistributedByResult(modelElementKey, label, viewResult));
        modelElementData.remove(modelElementKey);
      }
    }
    addMissingDistributions(modelElementData, distributedByModelElements, context);
    return distributedByModelElements;
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(getDistributedBy());
  }

  private void addMissingDistributions(
      final Map<String, FlowNodeDataDto> modelElementNames,
      final List<DistributedByResult> distributedByModelElements,
      final ExecutionContext<ProcessReportDataDto> context) {
    final Set<String> excludedFlowNodes =
        getExcludedFlowNodes(context.getReportData(), modelElementNames);
    // Only enrich distrBy buckets with flowNodes not excluded by executedFlowNode- or
    // identityFilters
    modelElementNames.keySet().stream()
        .filter(key -> !excludedFlowNodes.contains(key))
        .forEach(
            key ->
                distributedByModelElements.add(
                    DistributedByResult.createDistributedByResult(
                        key,
                        modelElementNames.get(key).getName(),
                        getViewPart().createEmptyResult(context))));
  }

  private Map<String, FlowNodeDataDto> getModelElementData(final ProcessReportDataDto reportData) {
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
        .map(this::extractModelElementData)
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
            .filter(data -> NOT_IN == data.getOperator())
            .flatMap(data -> data.getValues().stream())
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

  protected abstract String getModelElementIdPath();

  protected abstract Map<String, FlowNodeDataDto> extractModelElementData(
      DefinitionOptimizeResponseDto def);

  protected abstract ProcessReportDistributedByDto getDistributedBy();
}
