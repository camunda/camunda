/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.process.usertask.duration.groupby.candidategroup.distributedby.usertask;

import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import io.camunda.optimize.service.db.es.report.command.ProcessCmd;
import io.camunda.optimize.service.db.es.report.command.exec.ProcessReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.ProcessDistributedByUserTask;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.process.identity.ProcessGroupByCandidateGroup;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.duration.ProcessViewUserTaskDuration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserTaskDurationGroupByCandidateGroupByTaskCmd
    extends ProcessCmd<List<HyperMapResultEntryDto>> {

  public UserTaskDurationGroupByCandidateGroupByTaskCmd(
      final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<HyperMapResultEntryDto>> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder) {
    return builder
        .createExecutionPlan()
        .processCommand()
        .view(ProcessViewUserTaskDuration.class)
        .groupBy(ProcessGroupByCandidateGroup.class)
        .distributedBy(ProcessDistributedByUserTask.class)
        .resultAsHyperMap()
        .build();
  }
}
