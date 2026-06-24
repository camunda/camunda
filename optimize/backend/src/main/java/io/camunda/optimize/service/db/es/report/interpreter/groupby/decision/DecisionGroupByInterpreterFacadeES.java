/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.decision;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionGroupByInterpreterFacadeES
    extends AbstractInterpreterFacade<DecisionGroupBy, DecisionGroupByInterpreterES>
    implements DecisionGroupByInterpreterES {

  public DecisionGroupByInterpreterFacadeES(final List<DecisionGroupByInterpreterES> interpreters) {
    super(interpreters, DecisionGroupByInterpreterES::getSupportedGroupBys);
  }

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return interpretersMap.keySet();
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    interpreter(context.getPlan().getGroupBy())
        .adjustSearchRequest(searchRequestBuilder, baseQueryBuilder, context);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getGroupBy()).createAggregation(boolQuery, context);
  }

  @Override
  public CompositeCommandResult retrieveQueryResult(
      final ResponseBody<?> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getGroupBy()).retrieveQueryResult(response, context);
  }
}
