/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.decision;

import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionView.DECISION_VIEW_INSTANCE_FREQUENCY;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DecisionViewInstanceFrequencyInterpreterOS implements DecisionViewInterpreterOS {
  @Override
  public Set<DecisionView> getSupportedViews() {
    return Set.of(DECISION_VIEW_INSTANCE_FREQUENCY);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return Map.of(FREQUENCY_AGGREGATION, new Aggregation.Builder().filter(matchAll()).build());
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final long count = aggs.get(FREQUENCY_AGGREGATION).filter().docCount();
    return createViewResult(count);
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    // for instance count the default is 0
    // see https://jira.camunda.com/browse/OPT-3336
    return createViewResult(0.);
  }

  private ViewResult createViewResult(final double value) {
    return ViewResult.builder()
        .viewMeasure(CompositeCommandResult.ViewMeasure.builder().value(value).build())
        .build();
  }
}
