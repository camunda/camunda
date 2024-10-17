/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.model;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;
import static java.util.stream.Collectors.toSet;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.AbstractProcessDistributedByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractProcessDistributedByModelElementInterpreterES
    extends AbstractProcessDistributedByInterpreterES {
  private static final String MODEL_ELEMENT_ID_TERMS_AGGREGATION = "modelElement";

  protected abstract ConfigurationService getConfigurationService();

  protected abstract DefinitionService getDefinitionService();

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      BoolQuery baseQueryBuilder) {
    Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.size(
                            getConfigurationService()
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit())
                        .order(NamedValue.of("_key", SortOrder.Asc))
                        .field(getModelElementIdPath()));

    getViewInterpreter()
        .createAggregations(context)
        .forEach((k, v) -> builder.aggregations(k, v.build()));
    return Map.of(MODEL_ELEMENT_ID_TERMS_AGGREGATION, builder);
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final StringTermsAggregate byModelElementAggregation =
        aggregations.get(MODEL_ELEMENT_ID_TERMS_AGGREGATION).sterms();
    final Map<String, FlowNodeDataDto> modelElementData =
        getModelElementData(context.getReportData());
    final List<CompositeCommandResult.DistributedByResult> distributedByModelElements =
        new ArrayList<>();
    for (StringTermsBucket modelElementBucket : byModelElementAggregation.buckets().array()) {
      final CompositeCommandResult.ViewResult viewResult =
          getViewInterpreter().retrieveResult(response, modelElementBucket.aggregations(), context);
      final String modelElementKey = modelElementBucket.key().stringValue();
      if (modelElementData.containsKey(modelElementKey)) {
        String label = modelElementData.get(modelElementKey).getName();
        distributedByModelElements.add(
            createDistributedByResult(modelElementKey, label, viewResult));
        modelElementData.remove(modelElementKey);
      }
    }
    addMissingDistributions(modelElementData, distributedByModelElements, context);
    return distributedByModelElements;
  }

  protected abstract String getModelElementIdPath();

  protected abstract Map<String, FlowNodeDataDto> extractModelElementData(
      DefinitionOptimizeResponseDto def);

  private void addMissingDistributions(
      final Map<String, FlowNodeDataDto> modelElementNames,
      final List<CompositeCommandResult.DistributedByResult> distributedByModelElements,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Set<String> excludedFlowNodes =
        getExcludedFlowNodes(context.getReportData(), modelElementNames);
    // Only enrich distrBy buckets with flowNodes not excluded by executedFlowNode- or
    // identityFilters
    modelElementNames.keySet().stream()
        .filter(key -> !excludedFlowNodes.contains(key))
        .forEach(
            key ->
                distributedByModelElements.add(
                    CompositeCommandResult.DistributedByResult.createDistributedByResult(
                        key,
                        modelElementNames.get(key).getName(),
                        getViewInterpreter().createEmptyResult(context))));
  }

  private Map<String, FlowNodeDataDto> getModelElementData(final ProcessReportDataDto reportData) {
    return reportData.getDefinitions().stream()
        .map(
            definitionDto ->
                getDefinitionService()
                    .getDefinition(
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
    Set<String> excludedFlowNodes =
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
