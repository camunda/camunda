/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtilES.createIncidentAggregationFilter;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_INCIDENT_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_ACTIVITY_ID;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
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
public class GroupByIncidentFlowNodeInterpreterES extends AbstractGroupByFlowNodeInterpreterES {

  private static final String NESTED_INCIDENT_AGGREGATION = "nestedIncidentAggregation";
  private static final String GROUPED_BY_FLOW_NODE_ID_AGGREGATION =
      "groupedByFlowNodeIdAggregation";
  private static final String FILTERED_INCIDENT_AGGREGATION = "filteredIncidentAggregation";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  final ProcessViewInterpreterFacadeES viewInterpreter;

  public GroupByIncidentFlowNodeInterpreterES(
      ConfigurationService configurationService,
      DefinitionService definitionService,
      ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      ProcessViewInterpreterFacadeES viewInterpreter) {
    this.configurationService = configurationService;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_INCIDENT_FLOW_NODE);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().nested(n -> n.path(INCIDENTS));
    builder.aggregations(
        FILTERED_INCIDENT_AGGREGATION,
        Aggregation.of(
            a ->
                a.filter(
                        f ->
                            f.bool(
                                createIncidentAggregationFilter(
                                        context.getReportData(), definitionService)
                                    .build()))
                    .aggregations(
                        GROUPED_BY_FLOW_NODE_ID_AGGREGATION,
                        Aggregation.of(
                            a1 -> {
                              Aggregation.Builder.ContainerBuilder terms =
                                  a1.terms(
                                      t ->
                                          t.field(INCIDENTS + "." + INCIDENT_ACTIVITY_ID)
                                              .size(
                                                  configurationService
                                                      .getElasticSearchConfiguration()
                                                      .getAggregationBucketLimit()));
                              getDistributedByInterpreter()
                                  .createAggregations(context, boolQuery)
                                  .forEach((k, v) -> terms.aggregations(k, v.build()));
                              return terms;
                            }))));
    return Map.of(NESTED_INCIDENT_AGGREGATION, builder);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final NestedAggregate nestedAgg =
        response.aggregations().get(NESTED_INCIDENT_AGGREGATION).nested();
    final FilterAggregate filterAgg =
        nestedAgg.aggregations().get(FILTERED_INCIDENT_AGGREGATION).filter();
    final StringTermsAggregate groupedByFlowNodeId =
        filterAgg.aggregations().get(GROUPED_BY_FLOW_NODE_ID_AGGREGATION).sterms();

    final Map<String, String> flowNodeNames = getFlowNodeNames(context.getReportData());
    final List<CompositeCommandResult.GroupByResult> groupedData = new ArrayList<>();
    for (StringTermsBucket flowNodeBucket : groupedByFlowNodeId.buckets().array()) {
      final String flowNodeKey = flowNodeBucket.key().stringValue();
      if (flowNodeNames.containsKey(flowNodeKey)) {
        final List<CompositeCommandResult.DistributedByResult> singleResult =
            getDistributedByInterpreter()
                .retrieveResult(response, flowNodeBucket.aggregations(), context);
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
                        getDistributedByInterpreter().createEmptyResult(context));
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

  public DefinitionService getDefinitionService() {
    return this.definitionService;
  }

  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
