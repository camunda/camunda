/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtil.createIncidentAggregationFilter;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_INCIDENT_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_ACTIVITY_ID;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class GroupByIncidentFlowNodeInterpreterES extends AbstractGroupByFlowNodeInterpreterES {
  private static final String NESTED_INCIDENT_AGGREGATION = "nestedIncidentAggregation";
  private static final String GROUPED_BY_FLOW_NODE_ID_AGGREGATION =
      "groupedByFlowNodeIdAggregation";
  private static final String FILTERED_INCIDENT_AGGREGATION = "filteredIncidentAggregation";

  private final ConfigurationService configurationService;
  @Getter private final DefinitionService definitionService;
  @Getter final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  @Getter final ProcessViewInterpreterFacadeES viewInterpreter;

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_INCIDENT_FLOW_NODE);
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final TermsAggregationBuilder incidentTermsAggregation =
        terms(GROUPED_BY_FLOW_NODE_ID_AGGREGATION)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .field(INCIDENTS + "." + INCIDENT_ACTIVITY_ID);
    distributedByInterpreter
        .createAggregations(context, searchSourceBuilder.query())
        .forEach(incidentTermsAggregation::subAggregation);
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
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Nested nestedAgg = response.getAggregations().get(NESTED_INCIDENT_AGGREGATION);
    final Filter filterAgg = nestedAgg.getAggregations().get(FILTERED_INCIDENT_AGGREGATION);
    final Terms groupedByFlowNodeId =
        filterAgg.getAggregations().get(GROUPED_BY_FLOW_NODE_ID_AGGREGATION);

    final Map<String, String> flowNodeNames = getFlowNodeNames(context.getReportData());
    final List<CompositeCommandResult.GroupByResult> groupedData = new ArrayList<>();
    for (Terms.Bucket flowNodeBucket : groupedByFlowNodeId.getBuckets()) {
      final String flowNodeKey = flowNodeBucket.getKeyAsString();
      if (flowNodeNames.containsKey(flowNodeKey)) {
        final List<CompositeCommandResult.DistributedByResult> singleResult =
            distributedByInterpreter.retrieveResult(
                response, flowNodeBucket.getAggregations(), context);
        String label = flowNodeNames.get(flowNodeKey);
        groupedData.add(
            CompositeCommandResult.GroupByResult.createGroupByResult(
                flowNodeKey, label, singleResult));
        flowNodeNames.remove(flowNodeKey);
      }
    }
    addMissingGroupByIncidentKeys(flowNodeNames, groupedData, context);
    compositeCommandResult.setGroups(groupedData);
  }

  private void addMissingGroupByIncidentKeys(
      final Map<String, String> flowNodeNames,
      final List<CompositeCommandResult.GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
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
                CompositeCommandResult.GroupByResult emptyResult =
                    CompositeCommandResult.GroupByResult.createGroupByResult(
                        flowNodeKey,
                        flowNodeNames.get(flowNodeKey),
                        distributedByInterpreter.createEmptyResult(context));
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
