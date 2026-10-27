/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.decision;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.interpreter.plan.decision.DecisionExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.interpreter.plan.decision.DecisionExecutionPlanInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionExecutionPlanInterpreterFacadeES
    extends AbstractInterpreterFacade<DecisionExecutionPlan, DecisionExecutionPlanInterpreter>
    implements DecisionExecutionPlanInterpreterFacade {

  public DecisionExecutionPlanInterpreterFacadeES(
      final List<DecisionExecutionPlanInterpreter> interpreters) {
    super(interpreters, DecisionExecutionPlanInterpreter::getSupportedExecutionPlans);
  }

  @Override
  public Set<DecisionExecutionPlan> getSupportedExecutionPlans() {
    return interpretersMap.keySet();
  }

  @Override
  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return interpreter(context.getPlan()).interpret(context);
  }
}
