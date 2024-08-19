/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.exec.builder;

import org.springframework.stereotype.Component;

@Component
public class ReportCmdExecutionPlanBuilder {

  private final ProcessExecutionPlanBuilder processExecutionPlanBuilder;
  private final DecisionExecutionPlanBuilder decisionExecutionPlanBuilder;

  public ReportCmdExecutionPlanBuilder(
      final ProcessExecutionPlanBuilder processExecutionPlanBuilder,
      final DecisionExecutionPlanBuilder decisionExecutionPlanBuilder) {
    this.processExecutionPlanBuilder = processExecutionPlanBuilder;
    this.decisionExecutionPlanBuilder = decisionExecutionPlanBuilder;
  }

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
