/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.result;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.ReportResultType;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;

public class ResultInterpreter {
  public static CommandEvaluationResult interpret(
      ExecutionContext executionContext, CompositeCommandResult result) {
    return switch (getReportResultType(executionContext)) {
      case HYPER_MAP -> result.transformToHyperMap();
      case MAP -> result.transformToMap();
      case NUMBER -> result.transformToNumber();
      case RAW_DATA -> result.transformToRawData();
    };
  }

  private static ReportResultType getReportResultType(ExecutionContext executionContext) {
    ReportResultType reportResultType;
    if (executionContext.getPlan() instanceof DecisionExecutionPlan decisionExecutionPlan) {
      return decisionExecutionPlan.getResultType();
    }
    if (executionContext.getPlan() instanceof ProcessExecutionPlan processExecutionPlan) {
      return processExecutionPlan.getResultType();
    }
    throw new OptimizeRuntimeException(
        "Unable to extract report result type from " + executionContext.getPlan());
  }
}
