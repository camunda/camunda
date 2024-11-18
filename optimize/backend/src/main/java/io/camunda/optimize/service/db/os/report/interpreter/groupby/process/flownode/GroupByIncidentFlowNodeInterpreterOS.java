/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.termAggregation;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_INCIDENT_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_ACTIVITY_ID;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.filter.util.IncidentFilterQueryUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.groupby.flownode.GroupByIncidentFlowNodeInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class GroupByIncidentFlowNodeInterpreterOS extends AbstractGroupByFlowNodeInterpreterOS {

  private static final String NESTED_INCIDENT_AGGREGATION = "nestedIncidentAggregation";
  private static final String GROUPED_BY_FLOW_NODE_ID_AGGREGATION =
      "groupedByFlowNodeIdAggregation";
  private static final String FILTERED_INCIDENT_AGGREGATION = "filteredIncidentAggregation";
  final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final ConfigurationService configurationService;
  private final GroupByIncidentFlowNodeInterpreterHelper helper;
  private final DefinitionService definitionService;

  public GroupByIncidentFlowNodeInterpreterOS(
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final ConfigurationService configurationService,
      final GroupByIncidentFlowNodeInterpreterHelper helper,
      final DefinitionService definitionService) {
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
    this.helper = helper;
    this.definitionService = definitionService;
  }

  @Override
  protected DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_INCIDENT_FLOW_NODE);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final int size = configurationService.getOpenSearchConfiguration().getAggregationBucketLimit();
    final Aggregation groupedByFlowNodeIdAggregation =
        new Aggregation.Builder()
            .terms(termAggregation(INCIDENTS + "." + INCIDENT_ACTIVITY_ID, size))
            .aggregations(getDistributedByInterpreter().createAggregations(context, query))
            .build();
    final Aggregation filteredIncidentAggregation =
        new Aggregation.Builder()
            .filter(
                IncidentFilterQueryUtilOS.createIncidentAggregationFilterQuery(
                    context.getReportData(), definitionService))
            .aggregations(GROUPED_BY_FLOW_NODE_ID_AGGREGATION, groupedByFlowNodeIdAggregation)
            .build();
    final Aggregation aggregation =
        new Aggregation.Builder()
            .nested(n -> n.path(INCIDENTS))
            .aggregations(FILTERED_INCIDENT_AGGREGATION, filteredIncidentAggregation)
            .build();
    return Map.of(NESTED_INCIDENT_AGGREGATION, aggregation);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final NestedAggregate nestedAgg =
        response.aggregations().get(NESTED_INCIDENT_AGGREGATION).nested();
    final FilterAggregate filterAgg =
        nestedAgg.aggregations().get(FILTERED_INCIDENT_AGGREGATION).filter();
    final StringTermsAggregate groupedByFlowNodeId =
        filterAgg.aggregations().get(GROUPED_BY_FLOW_NODE_ID_AGGREGATION).sterms();

    final Map<String, String> flowNodeNames = helper.getFlowNodeNames(context.getReportData());
    final List<CompositeCommandResult.GroupByResult> groupedData = new ArrayList<>();
    for (final StringTermsBucket flowNodeBucket : groupedByFlowNodeId.buckets().array()) {
      final String flowNodeKey = flowNodeBucket.key();
      if (flowNodeNames.containsKey(flowNodeKey)) {
        final List<CompositeCommandResult.DistributedByResult> singleResult =
            getDistributedByInterpreter()
                .retrieveResult(response, flowNodeBucket.aggregations(), context);
        final String label = flowNodeNames.get(flowNodeKey);
        groupedData.add(
            CompositeCommandResult.GroupByResult.createGroupByResult(
                flowNodeKey, label, singleResult));
        flowNodeNames.remove(flowNodeKey);
      }
    }
    GroupByIncidentFlowNodeInterpreterHelper.addMissingGroupByIncidentKeys(
        flowNodeNames, groupedData, context, distributedByInterpreter.createEmptyResult(context));
    compositeCommandResult.setGroups(groupedData);
  }

  @Override
  protected ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  protected ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
