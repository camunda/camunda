/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.decision;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByMatchedRuleDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.MATCHED_RULES;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionGroupByMatchedRule extends DecisionGroupByPart {

  private final ConfigurationService configurationService;

  private static final String MATCHED_RULES_AGGREGATION = "matchedRules";

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<DecisionReportDataDto> context) {
    final TermsAggregationBuilder byMatchedRuleAggregation = AggregationBuilders
      .terms(MATCHED_RULES_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(MATCHED_RULES);
    distributedByPart.createAggregations(context).forEach(byMatchedRuleAggregation::subAggregation);
    return Collections.singletonList(byMatchedRuleAggregation);
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<DecisionReportDataDto> context) {

    final Terms matchedRuleTerms = response.getAggregations().get(MATCHED_RULES_AGGREGATION);
    final List<GroupByResult> matchedRules = new ArrayList<>();
    for (Terms.Bucket matchedRuleBucket : matchedRuleTerms.getBuckets()) {
      final List<DistributedByResult> distributions =
        distributedByPart.retrieveResult(response, matchedRuleBucket.getAggregations(), context);
      matchedRules.add(GroupByResult.createGroupByResult(matchedRuleBucket.getKeyAsString(), distributions));
    }

    compositeCommandResult.setGroups(matchedRules);
  }


  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto reportData) {
    reportData.setGroupBy(new DecisionGroupByMatchedRuleDto());
  }

}
