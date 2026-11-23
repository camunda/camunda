/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.agent;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentRunTokenViews;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.MapUtil;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Sums or averages a token field over agent runs, one view per token field. */
@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewAgentRunTokensInterpreterOS
    extends AbstractProcessViewAgentMetricInterpreterOS {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return AgentRunTokenViews.FIELD_BY_VIEW.keySet();
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final String field = AgentRunTokenViews.FIELD_BY_VIEW.get(context.getPlan().getView());
    return getAggregationStrategies(context.getReportData()).stream()
        .map(strategy -> strategy.createAggregation((Script) null, field))
        .collect(MapUtil.pairCollector());
  }
}
