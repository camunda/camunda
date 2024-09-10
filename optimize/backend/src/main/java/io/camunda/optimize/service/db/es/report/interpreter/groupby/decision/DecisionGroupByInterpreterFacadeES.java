/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.decision;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionGroupByInterpreterFacadeES
    extends AbstractInterpreterFacade<DecisionGroupBy, DecisionGroupByInterpreterES>
    implements DecisionGroupByInterpreterES {

  public DecisionGroupByInterpreterFacadeES(List<DecisionGroupByInterpreterES> interpreters) {
    super(interpreters, DecisionGroupByInterpreterES::getSupportedGroupBys);
  }

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return interpretersMap.keySet();
  }

  @Override
  public void adjustSearchRequest(
      SearchRequest searchRequest,
      BoolQueryBuilder baseQuery,
      ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    interpreter(context.getPlan().getGroupBy())
        .adjustSearchRequest(searchRequest, baseQuery, context);
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      SearchSourceBuilder searchSourceBuilder,
      ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getGroupBy())
        .createAggregation(searchSourceBuilder, context);
  }

  @Override
  public CompositeCommandResult retrieveQueryResult(
      SearchResponse response,
      ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan().getGroupBy()).retrieveQueryResult(response, context);
  }
}
