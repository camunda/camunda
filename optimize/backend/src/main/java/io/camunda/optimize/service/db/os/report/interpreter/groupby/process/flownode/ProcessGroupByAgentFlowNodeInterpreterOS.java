/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.termAggregation;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper.AdHocSubProcessStructure;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByAgentFlowNodeInterpreterOS extends AbstractProcessGroupByInterpreterOS {

  private static final String AGENT_INSTANCES_AGG = "agentInstances";
  private static final String BY_FLOW_NODE_ID_AGG = "byFlowNodeId";
  private static final String FLOW_NODE_INSTANCES_AGG = "flowNodeInstances";
  private static final String BY_FLOW_NODE_INSTANCE_ID_AGG = "byFlowNodeInstanceId";

  private final ConfigurationService configurationService;
  private final ProcessGroupByFlowNodeInterpreterHelper helper;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByAgentFlowNodeInterpreterOS(
      final ConfigurationService configurationService,
      final ProcessGroupByFlowNodeInterpreterHelper helper,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.configurationService = configurationService;
    this.helper = helper;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_AGENT_FLOW_NODE);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final int size = configurationService.getOpenSearchConfiguration().getAggregationBucketLimit();
    final Aggregation termsAgg =
        new Aggregation.Builder()
            .terms(termAggregation(AGENT_INSTANCES + "." + AGENT_INSTANCE_FLOW_NODE_ID, size))
            .aggregations(distributedByInterpreter.createAggregations(context, query))
            .build();
    final Aggregation nestedAgg =
        new Aggregation.Builder()
            .nested(n -> n.path(AGENT_INSTANCES))
            .aggregations(BY_FLOW_NODE_ID_AGG, termsAgg)
            .build();

    // Only add the (extra) flow-node-instance aggregation used to break an ad-hoc subprocess down
    // into per-tool heat when the model actually contains ad-hoc subprocess tool nodes. Reports
    // without them keep the exact same aggregation as before.
    if (helper.resolveAdHocSubProcessStructure(context).childIds().isEmpty()) {
      return Map.of(AGENT_INSTANCES_AGG, nestedAgg);
    }

    final Aggregation flowNodeTermsAgg =
        new Aggregation.Builder()
            .terms(termAggregation(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID, size))
            .build();
    final Aggregation flowNodeNestedAgg =
        new Aggregation.Builder()
            .nested(n -> n.path(FLOW_NODE_INSTANCES))
            .aggregations(BY_FLOW_NODE_INSTANCE_ID_AGG, flowNodeTermsAgg)
            .build();

    return Map.of(AGENT_INSTANCES_AGG, nestedAgg, FLOW_NODE_INSTANCES_AGG, flowNodeNestedAgg);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (response.aggregations() == null || response.aggregations().isEmpty()) {
      return;
    }

    final List<StringTermsBucket> agentBuckets = extractAgentBuckets(response);
    final List<StringTermsBucket> innerToolBuckets = extractInnerToolBuckets(response);
    if (agentBuckets.isEmpty() && innerToolBuckets.isEmpty()) {
      // No agent flow-node buckets located under the nested path: nothing to map (null-safe).
      return;
    }

    final AdHocSubProcessStructure adHocSubProcessStructure =
        helper.resolveAdHocSubProcessStructure(context);

    compositeCommandResult.setGroups(
        helper.mapAgentFlowNodeBucketsToGroupByResults(
            agentBuckets,
            StringTermsBucket::key,
            bucket ->
                distributedByInterpreter.retrieveResult(response, bucket.aggregations(), context),
            innerToolBuckets,
            StringTermsBucket::key,
            bucket ->
                helper.toFrequencyResult(
                    distributedByInterpreter.createEmptyResult(context), bucket.docCount()),
            adHocSubProcessStructure,
            context,
            distributedByInterpreter.createEmptyResult(context)));
  }

  private List<StringTermsBucket> extractAgentBuckets(final SearchResponse<RawResult> response) {
    return Optional.ofNullable(response.aggregations().get(AGENT_INSTANCES_AGG))
        .map(Aggregate::nested)
        .map(nested -> nested.aggregations().get(BY_FLOW_NODE_ID_AGG).sterms())
        .map(sterms -> sterms.buckets().array())
        .orElseGet(List::of);
  }

  private List<StringTermsBucket> extractInnerToolBuckets(
      final SearchResponse<RawResult> response) {
    return Optional.ofNullable(response.aggregations().get(FLOW_NODE_INSTANCES_AGG))
        .map(Aggregate::nested)
        .map(nested -> nested.aggregations().get(BY_FLOW_NODE_INSTANCE_ID_AGG).sterms())
        .map(sterms -> sterms.buckets().array())
        .orElseGet(List::of);
  }

  @Override
  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
