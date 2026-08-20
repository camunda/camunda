/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.process;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.interpreter.plan.process.ProcessExecutionPlanInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessExecutionPlanInterpreterFacadeOS
    extends AbstractInterpreterFacade<ProcessExecutionPlan, ProcessExecutionPlanInterpreterOS>
    implements ProcessExecutionPlanInterpreterFacade, ProcessExecutionPlanInterpreterOS {

  public ProcessExecutionPlanInterpreterFacadeOS(
      final List<ProcessExecutionPlanInterpreterOS> interpreters) {
    super(interpreters, ProcessExecutionPlanInterpreterOS::getSupportedExecutionPlans);
  }

  @Override
  public Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return interpretersMap.keySet();
  }

  @Override
  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan()).interpret(context);
  }

  @Override
  public Optional<MinMaxStatDto> getGroupByMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan()).getGroupByMinMaxStats(context);
  }

  @Override
  public BoolQuery.Builder baseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan()).baseQueryBuilder(context);
  }
}
