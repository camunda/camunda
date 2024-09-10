/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.decision;

import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionView.DECISION_VIEW_INSTANCE_FREQUENCY;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionViewInstanceFrequencyInterpreterES implements DecisionViewInterpreterES {
  @Override
  public Set<DecisionView> getSupportedViews() {
    return Set.of(DECISION_VIEW_INSTANCE_FREQUENCY);
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return Collections.singletonList(filter(FREQUENCY_AGGREGATION, QueryBuilders.matchAllQuery()));
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse response,
      final Aggregations aggs,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final Filter count = aggs.get(FREQUENCY_AGGREGATION);
    return createViewResult(count.getDocCount());
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
