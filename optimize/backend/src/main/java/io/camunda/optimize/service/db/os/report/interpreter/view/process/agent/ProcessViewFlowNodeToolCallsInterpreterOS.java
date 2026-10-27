/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.agent;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_FLOW_NODE_TOOL_CALLS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_METRICS_TOOL_CALLS;

import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Aggregates per-flow-node tool-call counts from the nested {@code agentInstances} documents
 * (unlike {@link ProcessViewAgentToolCallsInterpreterOS}, which reads the flat process-level rollup
 * field).
 *
 * <p>The returned field name is relative to the {@code agentInstances} nested path, so this view is
 * only valid when its metric aggregation is placed inside a nested {@code agentInstances}
 * aggregation. That nested context is established by {@code PROCESS_GROUP_BY_AGENT_FLOW_NODE},
 * which is the only group-by this view is paired with in {@code ProcessExecutionPlan}. Pairing it
 * with a non-nested group-by would leave the field path unresolved and produce empty results.
 */
@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewFlowNodeToolCallsInterpreterOS
    extends AbstractProcessViewAgentMetricInterpreterOS {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_FLOW_NODE_TOOL_CALLS);
  }

  @Override
  protected String getAggregationFieldName() {
    return AGENT_INSTANCES + "." + AGENT_INSTANCE_METRICS_TOOL_CALLS;
  }
}
