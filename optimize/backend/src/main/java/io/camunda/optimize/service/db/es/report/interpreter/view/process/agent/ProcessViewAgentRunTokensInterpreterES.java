/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agent;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentRunTokenViews;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Sums or averages a token field over agent runs, one view per token field. */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewAgentRunTokensInterpreterES
    extends AbstractProcessViewAgentMetricInterpreterES {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return AgentRunTokenViews.FIELD_BY_VIEW.keySet();
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final String field = AgentRunTokenViews.FIELD_BY_VIEW.get(context.getPlan().getView());
    return getAggregationStrategies(context.getReportData()).stream()
        .map(strategy -> strategy.createAggregationBuilder((Script) null, field))
        .collect(Collectors.toMap(Pair::key, Pair::value));
  }
}
