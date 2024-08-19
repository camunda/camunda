/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process.flownode;

import static io.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtil.createIncidentAggregationFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_ACTIVITY_ID;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.process.ProcessGroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GroupByIncidentFlowNode extends ProcessGroupByPart {

  private static final String NESTED_INCIDENT_AGGREGATION = "nestedIncidentAggregation";
  private static final String GROUPED_BY_FLOW_NODE_ID_AGGREGATION =
      "groupedByFlowNodeIdAggregation";
  private static final String FILTERED_INCIDENT_AGGREGATION = "filteredIncidentAggregation";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;

  public GroupByIncidentFlowNode(
      final ConfigurationService configurationService, final DefinitionService definitionService) {
    this.configurationService = configurationService;
    this.definitionService = definitionService;
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto> context) {
    final TermsAggregationBuilder incidentTermsAggregation =
        terms(GROUPED_BY_FLOW_NODE_ID_AGGREGATION)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .field(INCIDENTS + "." + INCIDENT_ACTIVITY_ID);
    distributedByPart.createAggregations(context).forEach(incidentTermsAggregation::subAggregation);
    return Collections.singletonList(
        nested(NESTED_INCIDENT_AGGREGATION, INCIDENTS)
            .subAggregation(
                filter(
                        FILTERED_INCIDENT_AGGREGATION,
                        createIncidentAggregationFilter(context.getReportData(), definitionService))
                    .subAggregation(incidentTermsAggregation)));
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto> context) {
    final Nested nestedAgg = response.getAggregations().get(NESTED_INCIDENT_AGGREGATION);
    final Filter filterAgg = nestedAgg.getAggregations().get(FILTERED_INCIDENT_AGGREGATION);
    final Terms groupedByFlowNodeId =
        filterAgg.getAggregations().get(GROUPED_BY_FLOW_NODE_ID_AGGREGATION);

    final Map<String, String> flowNodeNames = getFlowNodeNames(context.getReportData());
    final List<CompositeCommandResult.GroupByResult> groupedData = new ArrayList<>();
    for (final Terms.Bucket flowNodeBucket : groupedByFlowNodeId.getBuckets()) {
      final String flowNodeKey = flowNodeBucket.getKeyAsString();
      if (flowNodeNames.containsKey(flowNodeKey)) {
        final List<CompositeCommandResult.DistributedByResult> singleResult =
            distributedByPart.retrieveResult(response, flowNodeBucket.getAggregations(), context);
        final String label = flowNodeNames.get(flowNodeKey);
        groupedData.add(
            CompositeCommandResult.GroupByResult.createGroupByResult(
                flowNodeKey, label, singleResult));
        flowNodeNames.remove(flowNodeKey);
      }
    }
    addMissingGroupByIncidentKeys(flowNodeNames, groupedData, context);
    compositeCommandResult.setGroups(groupedData);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new FlowNodesGroupByDto());
  }

  private void addMissingGroupByIncidentKeys(
      final Map<String, String> flowNodeNames,
      final List<CompositeCommandResult.GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto> context) {
    final boolean viewLevelFilterExists =
        context.getReportData().getFilter().stream()
            .anyMatch(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()));
    // If a view level filter exists, the data should not be enriched as the missing data has been
    // omitted by the filters
    if (!viewLevelFilterExists) {
      // enrich data with flow nodes that haven't been executed, but should still show up in the
      // result
      flowNodeNames
          .keySet()
          .forEach(
              flowNodeKey -> {
                final CompositeCommandResult.GroupByResult emptyResult =
                    CompositeCommandResult.GroupByResult.createGroupByResult(
                        flowNodeKey,
                        flowNodeNames.get(flowNodeKey),
                        distributedByPart.createEmptyResult(context));
                groupedData.add(emptyResult);
              });
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
}
