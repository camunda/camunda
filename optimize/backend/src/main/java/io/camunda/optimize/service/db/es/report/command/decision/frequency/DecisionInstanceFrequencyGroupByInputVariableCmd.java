/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.decision.frequency;

import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.db.es.report.command.DecisionCmd;
import io.camunda.optimize.service.db.es.report.command.exec.DecisionReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.decision.variable.DecisionGroupByInputVariable;
import io.camunda.optimize.service.db.es.report.command.modules.view.decision.DecisionViewInstanceFrequency;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceFrequencyGroupByInputVariableCmd
    extends DecisionCmd<List<MapResultEntryDto>> {

  public DecisionInstanceFrequencyGroupByInputVariableCmd(
      final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected DecisionReportCmdExecutionPlan<List<MapResultEntryDto>> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder) {
    return builder
        .createExecutionPlan()
        .decisionCommand()
        .view(DecisionViewInstanceFrequency.class)
        .groupBy(DecisionGroupByInputVariable.class)
        .distributedBy(DecisionDistributedByNone.class)
        .resultAsMap()
        .build();
  }
}
