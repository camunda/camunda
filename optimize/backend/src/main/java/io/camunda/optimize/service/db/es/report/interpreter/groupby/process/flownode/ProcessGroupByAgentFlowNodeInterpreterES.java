/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper.AdHocSubProcessStructure;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByAgentFlowNodeInterpreterES extends AbstractProcessGroupByInterpreterES {

  private static final String AGENT_INSTANCES_AGG = "agentInstances";
  private static final String BY_FLOW_NODE_ID_AGG = "byFlowNodeId";
  private static final String FLOW_NODE_INSTANCES_AGG = "flowNodeInstances";
  private static final String BY_FLOW_NODE_INSTANCE_ID_AGG = "byFlowNodeInstanceId";

  private final ConfigurationService configurationService;
  private final ProcessGroupByFlowNodeInterpreterHelper helper;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessGroupByAgentFlowNodeInterpreterES(
      final ConfigurationService configurationService,
      final ProcessGroupByFlowNodeInterpreterHelper helper,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
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
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final int bucketLimit =
        configurationService.getElasticSearchConfiguration().getAggregationBucketLimit();

    final Aggregation.Builder.ContainerBuilder termsBuilder =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.size(bucketLimit).field(AGENT_INSTANCES + "." + AGENT_INSTANCE_FLOW_NODE_ID));
    distributedByInterpreter
        .createAggregations(context, boolQuery)
        .forEach((k, v) -> termsBuilder.aggregations(k, v.build()));

    final Aggregation.Builder.ContainerBuilder nestedBuilder =
        new Aggregation.Builder()
            .nested(n -> n.path(AGENT_INSTANCES))
            .aggregations(BY_FLOW_NODE_ID_AGG, termsBuilder.build());

    // Only add the (extra) flow-node-instance aggregation used to break an ad-hoc subprocess down
    // into per-tool heat when the model actually contains ad-hoc subprocess tool nodes. Reports
    // without them keep the exact same aggregation as before.
    if (helper.resolveAdHocSubProcessStructure(context).childIds().isEmpty()) {
      return Map.of(AGENT_INSTANCES_AGG, nestedBuilder);
    }

    final Aggregation.Builder.ContainerBuilder flowNodeTermsBuilder =
        new Aggregation.Builder()
            .terms(t -> t.size(bucketLimit).field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID));
    final Aggregation.Builder.ContainerBuilder flowNodeNestedBuilder =
        new Aggregation.Builder()
            .nested(n -> n.path(FLOW_NODE_INSTANCES))
            .aggregations(BY_FLOW_NODE_INSTANCE_ID_AGG, flowNodeTermsBuilder.build());

    return Map.of(
        AGENT_INSTANCES_AGG, nestedBuilder, FLOW_NODE_INSTANCES_AGG, flowNodeNestedBuilder);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
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
            bucket -> bucket.key().stringValue(),
            bucket ->
                distributedByInterpreter.retrieveResult(response, bucket.aggregations(), context),
            innerToolBuckets,
            bucket -> bucket.key().stringValue(),
            bucket ->
                helper.toFrequencyResult(
                    distributedByInterpreter.createEmptyResult(context), bucket.docCount()),
            adHocSubProcessStructure,
            context,
            distributedByInterpreter.createEmptyResult(context)));
  }

  private List<StringTermsBucket> extractAgentBuckets(final ResponseBody<?> response) {
    return Optional.ofNullable(response.aggregations().get(AGENT_INSTANCES_AGG))
        .map(Aggregate::nested)
        .map(nested -> nested.aggregations().get(BY_FLOW_NODE_ID_AGG).sterms())
        .map(sterms -> sterms.buckets().array())
        .orElseGet(List::of);
  }

  private List<StringTermsBucket> extractInnerToolBuckets(final ResponseBody<?> response) {
    return Optional.ofNullable(response.aggregations().get(FLOW_NODE_INSTANCES_AGG))
        .map(Aggregate::nested)
        .map(nested -> nested.aggregations().get(BY_FLOW_NODE_INSTANCE_ID_AGG).sterms())
        .map(sterms -> sterms.buckets().array())
        .orElseGet(List::of);
  }

  @Override
  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }
}
