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

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
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
    return Map.of(AGENT_INSTANCES_AGG, nestedAgg);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    Optional.ofNullable(response.aggregations())
        .filter(aggs -> !aggs.isEmpty())
        // null-safe: yields an empty Optional when the nested agg key is absent
        .map(aggs -> aggs.get(AGENT_INSTANCES_AGG))
        .map(Aggregate::nested)
        .map(nested -> nested.aggregations().get(BY_FLOW_NODE_ID_AGG).sterms())
        .ifPresent(
            byFlowNodeId ->
                compositeCommandResult.setGroups(
                    helper.mapFlowNodeBucketsToGroupByResults(
                        byFlowNodeId.buckets().array(),
                        StringTermsBucket::key,
                        bucket ->
                            distributedByInterpreter.retrieveResult(
                                response, bucket.aggregations(), context),
                        context,
                        distributedByInterpreter.createEmptyResult(context))));
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
