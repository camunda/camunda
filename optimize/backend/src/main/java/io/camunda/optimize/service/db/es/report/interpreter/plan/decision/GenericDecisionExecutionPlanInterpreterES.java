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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class GenericDecisionExecutionPlanInterpreterES
    extends AbstractDecisionExecutionPlanInterpreterES {

  private final DecisionDefinitionReader decisionDefinitionReader;
  private final DecisionQueryFilterEnhancerES queryFilterEnhancer;
  private final DecisionGroupByInterpreterFacadeES groupByInterpreter;
  private final DecisionViewInterpreterFacadeES viewInterpreter;
  private final OptimizeElasticsearchClient esClient;

  public GenericDecisionExecutionPlanInterpreterES(
      final DecisionDefinitionReader decisionDefinitionReader,
      final DecisionQueryFilterEnhancerES queryFilterEnhancer,
      final DecisionGroupByInterpreterFacadeES groupByInterpreter,
      final DecisionViewInterpreterFacadeES viewInterpreter,
      final OptimizeElasticsearchClient esClient) {
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.esClient = esClient;
  }

  @Override
  public Set<DecisionExecutionPlan> getSupportedExecutionPlans() {
    return Set.of(
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_EVALUATION_DATE_TIME,
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_INPUT_VARIABLE,
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_MATCHED_RULE,
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_NONE,
        DECISION_INSTANCE_FREQUENCY_GROUP_BY_OUTPUT_VARIABLE);
  }

  public DecisionDefinitionReader getDecisionDefinitionReader() {
    return this.decisionDefinitionReader;
  }

  public DecisionQueryFilterEnhancerES getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public DecisionGroupByInterpreterFacadeES getGroupByInterpreter() {
    return this.groupByInterpreter;
  }

  public DecisionViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }

  public OptimizeElasticsearchClient getEsClient() {
    return this.esClient;
  }
}
