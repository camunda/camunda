/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.decision;

import static io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan.DECISION_INSTANCE_FREQUENCY_GROUP_BY_EVALUATION_DATE_TIME;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan.DECISION_INSTANCE_FREQUENCY_GROUP_BY_INPUT_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan.DECISION_INSTANCE_FREQUENCY_GROUP_BY_MATCHED_RULE;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan.DECISION_INSTANCE_FREQUENCY_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan.DECISION_INSTANCE_FREQUENCY_GROUP_BY_OUTPUT_VARIABLE;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.decision.DecisionGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.decision.DecisionViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class GenericDecisionExecutionPlanInterpreterES
    extends AbstractDecisionExecutionPlanInterpreterES {
  @Getter private final DecisionDefinitionReader decisionDefinitionReader;
  @Getter private final DecisionQueryFilterEnhancerES queryFilterEnhancer;
  @Getter private final DecisionGroupByInterpreterFacadeES groupByInterpreter;
  @Getter private final DecisionViewInterpreterFacadeES viewInterpreter;
  @Getter private final OptimizeElasticsearchClient esClient;

  @Override
  public Set<DecisionExecutionPlan> getSupportedExecutionPlans() {
    return Set.of(
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_EVALUATION_DATE_TIME,
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_INPUT_VARIABLE,
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_MATCHED_RULE,
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_NONE,
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_OUTPUT_VARIABLE);
  }
}
