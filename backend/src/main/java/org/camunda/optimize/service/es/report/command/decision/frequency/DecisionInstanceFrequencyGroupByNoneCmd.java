/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.service.es.report.command.DecisionCmd;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.decision.DecisionGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewInstanceFrequency;
import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceFrequencyGroupByNoneCmd extends DecisionCmd<Double> {

  public DecisionInstanceFrequencyGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected DecisionReportCmdExecutionPlan<Double> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .decisionCommand()
      .view(DecisionViewInstanceFrequency.class)
      .groupBy(DecisionGroupByNone.class)
      .distributedBy(DecisionDistributedByNone.class)
      .resultAsNumber()
      .build();
  }

}