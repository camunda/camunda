/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.decision;

import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_MATCHED_RULE;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.MATCHED_RULES;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.decision.DecisionDistributedByNoneInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.decision.AbstractDecisionGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.decision.DecisionViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class DecisionGroupByMatchedRuleInterpreterES extends AbstractDecisionGroupByInterpreterES {
  private static final String MATCHED_RULES_AGGREGATION = "matchedRules";
  private final ConfigurationService configurationService;
  @Getter private final DecisionDistributedByNoneInterpreterES distributedByInterpreter;
  @Getter private final DecisionViewInterpreterFacadeES viewInterpreter;

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return Set.of(DECISION_GROUP_BY_MATCHED_RULE);
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final TermsAggregationBuilder byMatchedRuleAggregation =
        AggregationBuilders.terms(MATCHED_RULES_AGGREGATION)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .field(MATCHED_RULES);
    distributedByInterpreter
        .createAggregations(context, searchSourceBuilder.query())
        .forEach(byMatchedRuleAggregation::subAggregation);
    return Collections.singletonList(byMatchedRuleAggregation);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {

    final Terms matchedRuleTerms = response.getAggregations().get(MATCHED_RULES_AGGREGATION);
    final List<GroupByResult> matchedRules = new ArrayList<>();
    for (Terms.Bucket matchedRuleBucket : matchedRuleTerms.getBuckets()) {
      final List<DistributedByResult> distributions =
          distributedByInterpreter.retrieveResult(
              response, matchedRuleBucket.getAggregations(), context);
      matchedRules.add(
          GroupByResult.createGroupByResult(matchedRuleBucket.getKeyAsString(), distributions));
    }

    compositeCommandResult.setGroups(matchedRules);
  }
}
