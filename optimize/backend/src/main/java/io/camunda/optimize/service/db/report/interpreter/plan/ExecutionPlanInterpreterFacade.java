/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.plan;

import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.interpreter.plan.decision.DecisionExecutionPlanInterpreterFacade;
import io.camunda.optimize.service.db.report.interpreter.plan.process.ProcessExecutionPlanInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlanInterpreterFacade {

  private final ProcessExecutionPlanInterpreterFacade processExecutionPlanInterpreterFacade;
  private final DecisionExecutionPlanInterpreterFacade decisionExecutionPlanInterpreterFacade;

  public ExecutionPlanInterpreterFacade(
      final ProcessExecutionPlanInterpreterFacade processExecutionPlanInterpreterFacade,
      final DecisionExecutionPlanInterpreterFacade decisionExecutionPlanInterpreterFacade) {
    this.processExecutionPlanInterpreterFacade = processExecutionPlanInterpreterFacade;
    this.decisionExecutionPlanInterpreterFacade = decisionExecutionPlanInterpreterFacade;
  }

  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<? extends SingleReportDataDto, ? extends ExecutionPlan> context) {
    if (context.getPlan() instanceof DecisionExecutionPlan) {
      if (context.getReportData() instanceof DecisionReportDataDto) {
        @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
        final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan>
            decisionExecutionContext =
                (ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan>) context;
        return decisionExecutionPlanInterpreterFacade.interpret(decisionExecutionContext);
      }
    }

    if (context.getPlan() instanceof ProcessExecutionPlan) {
      if (context.getReportData() instanceof ProcessReportDataDto) {
        @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
        final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> processExecutionContext =
            (ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan>) context;
        return processExecutionPlanInterpreterFacade.interpret(processExecutionContext);
      }
    }

    throw new UnsupportedOperationException(
        format("No interpreter registred for plan=%s, context=%s", context.getPlan(), context));
  }

  public Optional<MinMaxStatDto> getGroupByMinMaxStats(final ExecutionContext context) {
    if (context.getPlan() instanceof ProcessExecutionPlan) {
      if (context.getReportData() instanceof ProcessReportDataDto) {
        @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
        final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> processExecutionContext =
            (ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan>) context;
        return processExecutionPlanInterpreterFacade.getGroupByMinMaxStats(processExecutionContext);
      }
    }

    return Optional.empty();
  }
}
