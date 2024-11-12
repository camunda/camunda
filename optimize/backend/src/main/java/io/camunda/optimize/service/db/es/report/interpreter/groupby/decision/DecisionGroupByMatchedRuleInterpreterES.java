/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.decision;

import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_MATCHED_RULE;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.MATCHED_RULES;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.decision.DecisionDistributedByNoneInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.decision.DecisionViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionGroupByMatchedRuleInterpreterES extends AbstractDecisionGroupByInterpreterES {

  private static final String MATCHED_RULES_AGGREGATION = "matchedRules";
  private final ConfigurationService configurationService;
  private final DecisionDistributedByNoneInterpreterES distributedByInterpreter;
  private final DecisionViewInterpreterFacadeES viewInterpreter;

  public DecisionGroupByMatchedRuleInterpreterES(
      final ConfigurationService configurationService,
      final DecisionDistributedByNoneInterpreterES distributedByInterpreter,
      final DecisionViewInterpreterFacadeES viewInterpreter) {
    this.configurationService = configurationService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return Set.of(DECISION_GROUP_BY_MATCHED_RULE);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.size(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit())
                        .field(MATCHED_RULES));
    getDistributedByInterpreter()
        .createAggregations(context, boolQuery)
        .forEach((k, v) -> builder.aggregations(k, v.build()));
    return Map.of(MATCHED_RULES_AGGREGATION, builder);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {

    final StringTermsAggregate matchedRuleTerms =
        response.aggregations().get(MATCHED_RULES_AGGREGATION).sterms();
    final List<CompositeCommandResult.GroupByResult> matchedRules = new ArrayList<>();
    for (final StringTermsBucket matchedRuleBucket : matchedRuleTerms.buckets().array()) {
      final List<CompositeCommandResult.DistributedByResult> distributions =
          getDistributedByInterpreter()
              .retrieveResult(response, matchedRuleBucket.aggregations(), context);
      matchedRules.add(
          CompositeCommandResult.GroupByResult.createGroupByResult(
              matchedRuleBucket.key().stringValue(), distributions));
    }

    compositeCommandResult.setGroups(matchedRules);
  }

  public DecisionDistributedByNoneInterpreterES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public DecisionViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
