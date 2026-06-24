/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.decision;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DecisionGroupByInterpreterFacadeOS
    extends AbstractInterpreterFacade<DecisionGroupBy, DecisionGroupByInterpreterOS>
    implements DecisionGroupByInterpreterOS {

  public DecisionGroupByInterpreterFacadeOS(final List<DecisionGroupByInterpreterOS> interpreters) {
    super(interpreters, DecisionGroupByInterpreterOS::getSupportedGroupBys);
  }

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return interpretersMap.keySet();
  }

  @Override
  public BoolQuery.Builder adjustQuery(
      final BoolQuery.Builder queryBuilder,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getGroupBy()).adjustQuery(queryBuilder, context);
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    interpreter(context.getPlan().getGroupBy())
        .adjustSearchRequest(searchRequestBuilder, baseQuery, context);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getGroupBy()).createAggregation(query, context);
  }

  @Override
  public CompositeCommandResult retrieveQueryResult(
      final SearchResponse<RawResult> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getGroupBy()).retrieveQueryResult(response, context);
  }
}
