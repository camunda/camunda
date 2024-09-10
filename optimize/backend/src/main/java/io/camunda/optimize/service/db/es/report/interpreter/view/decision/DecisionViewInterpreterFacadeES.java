/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.decision;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionViewInterpreterFacadeES
    extends AbstractInterpreterFacade<DecisionView, DecisionViewInterpreterES>
    implements DecisionViewInterpreterES {

  public DecisionViewInterpreterFacadeES(final List<DecisionViewInterpreterES> interpreters) {
    super(interpreters, DecisionViewInterpreterES::getSupportedViews);
  }

  @Override
  public Set<DecisionView> getSupportedViews() {
    return interpretersMap.keySet();
  }

  @Override
  public ViewProperty getViewProperty(
      ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).getViewProperty(context);
  }

  @Override
  public ViewResult createEmptyResult(
      ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).createEmptyResult(context);
  }

  public List<AggregationBuilder> createAggregations(
      ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).createAggregations(context);
  }

  @Override
  public ViewResult retrieveResult(
      SearchResponse response,
      Aggregations aggregations,
      ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).retrieveResult(response, aggregations, context);
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    interpreter(context.getPlan().getView()).adjustSearchRequest(searchRequest, baseQuery, context);
  }
}
