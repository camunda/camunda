/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.decision;

import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_MATCHED_RULE;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.MATCHED_RULES;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.decision.DecisionDistributedByNoneInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.decision.DecisionViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DecisionGroupByMatchedRuleInterpreterOS extends AbstractDecisionGroupByInterpreterOS {

  private static final String MATCHED_RULES_AGGREGATION = "matchedRules";
  private final ConfigurationService configurationService;
  private final DecisionDistributedByNoneInterpreterOS distributedByInterpreter;
  private final DecisionViewInterpreterFacadeOS viewInterpreter;

  public DecisionGroupByMatchedRuleInterpreterOS(
      final ConfigurationService configurationService,
      final DecisionDistributedByNoneInterpreterOS distributedByInterpreter,
      final DecisionViewInterpreterFacadeOS viewInterpreter) {
    this.configurationService = configurationService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return Set.of(DECISION_GROUP_BY_MATCHED_RULE);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return Map.of(
        MATCHED_RULES_AGGREGATION,
        new Aggregation.Builder()
            .terms(
                TermsAggregation.of(
                    b ->
                        b.size(
                                configurationService
                                    .getOpenSearchConfiguration()
                                    .getAggregationBucketLimit())
                            .field(MATCHED_RULES)))
            .aggregations(distributedByInterpreter.createAggregations(context, query))
            .build());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final List<GroupByResult> matchedRules =
        response.aggregations().get(MATCHED_RULES_AGGREGATION).sterms().buckets().array().stream()
            .map(
                bucket -> {
                  final List<DistributedByResult> distributions =
                      distributedByInterpreter.retrieveResult(
                          response, bucket.aggregations(), context);
                  return GroupByResult.createGroupByResult(bucket.key(), distributions);
                })
            .toList();

    compositeCommandResult.setGroups(matchedRules);
  }

  @Override
  public DecisionDistributedByNoneInterpreterOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public DecisionViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
