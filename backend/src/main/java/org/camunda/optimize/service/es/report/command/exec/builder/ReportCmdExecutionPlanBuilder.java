/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec.builder;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ReportCmdExecutionPlanBuilder {

  private final ProcessExecutionPlanBuilder processExecutionPlanBuilder;
  private final DecisionExecutionPlanBuilder decisionExecutionPlanBuilder;

  public DetermineCommandTypeBuilder createExecutionPlan() {
    return new DetermineCommandTypeBuilder();
  }

  public class DetermineCommandTypeBuilder {

    public ProcessExecutionPlanBuilder.AddViewPartBuilder processCommand() {
      return processExecutionPlanBuilder.createExecutionPlan();
    }

    public DecisionExecutionPlanBuilder.AddViewPartBuilder decisionCommand() {
      return decisionExecutionPlanBuilder.createExecutionPlan();
    }
  }



}
