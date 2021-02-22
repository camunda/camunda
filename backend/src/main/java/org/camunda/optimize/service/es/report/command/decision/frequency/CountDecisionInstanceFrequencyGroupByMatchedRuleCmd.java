/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.DecisionCmd;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.decision.DecisionGroupByMatchedRule;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewCountInstanceFrequency;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CountDecisionInstanceFrequencyGroupByMatchedRuleCmd extends DecisionCmd<List<MapResultEntryDto>> {

  public CountDecisionInstanceFrequencyGroupByMatchedRuleCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected DecisionReportCmdExecutionPlan<List<MapResultEntryDto>> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .decisionCommand()
      .view(DecisionViewCountInstanceFrequency.class)
      .groupBy(DecisionGroupByMatchedRule.class)
      .distributedBy(DecisionDistributedByNone.class)
      .resultAsMap()
      .build();
  }

}
