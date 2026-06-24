/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.decision;

import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.decision.DecisionDistributedByNoneInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.decision.DecisionViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DecisionGroupByNoneInterpreterOS extends AbstractDecisionGroupByInterpreterOS {

  private final DecisionDistributedByNoneInterpreterOS distributedByInterpreter;
  private final DecisionViewInterpreterFacadeOS viewInterpreter;

  public DecisionGroupByNoneInterpreterOS(
      final DecisionDistributedByNoneInterpreterOS distributedByInterpreter,
      final DecisionViewInterpreterFacadeOS viewInterpreter) {
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return Set.of(DECISION_GROUP_BY_NONE);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    // nothing to do here, since we don't group so just pass the view part on
    return distributedByInterpreter.createAggregations(context, query);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final List<DistributedByResult> distributions =
        distributedByInterpreter.retrieveResult(response, response.aggregations(), context);
    final GroupByResult groupByResult = GroupByResult.createGroupByNone(distributions);
    compositeCommandResult.setGroup(groupByResult);
  }

  public DecisionDistributedByNoneInterpreterOS getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public DecisionViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }
}
