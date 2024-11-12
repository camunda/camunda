/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.decision;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DecisionViewInterpreterFacadeOS
    extends AbstractInterpreterFacade<DecisionView, DecisionViewInterpreterOS>
    implements DecisionViewInterpreterOS {

  public DecisionViewInterpreterFacadeOS(final List<DecisionViewInterpreterOS> interpreters) {
    super(interpreters, DecisionViewInterpreterOS::getSupportedViews);
  }

  @Override
  public Set<DecisionView> getSupportedViews() {
    return interpretersMap.keySet();
  }

  @Override
  public ViewProperty getViewProperty(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).getViewProperty(context);
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).createEmptyResult(context);
  }

  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).createAggregations(context);
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).retrieveResult(response, aggregations, context);
  }

  @Override
  public BoolQuery.Builder adjustQuery(
      final BoolQuery.Builder queryBuilder,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getView()).adjustQuery(queryBuilder, context);
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    interpreter(context.getPlan().getView())
        .adjustSearchRequest(searchRequestBuilder, baseQuery, context);
  }
}
