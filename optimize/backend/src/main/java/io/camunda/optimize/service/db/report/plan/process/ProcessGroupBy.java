/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.group.AssigneeGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.CandidateGroupGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.RunningDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.UserTasksGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;

public enum ProcessGroupBy {
  PROCESS_GROUP_BY_ASSIGNEE(new AssigneeGroupByDto()),
  PROCESS_GROUP_BY_CANDIDATE_GROUP(new CandidateGroupGroupByDto()),
  PROCESS_GROUP_BY_DURATION(new DurationGroupByDto()),
  PROCESS_GROUP_BY_FLOW_NODE(new FlowNodesGroupByDto()),
  PROCESS_GROUP_BY_FLOW_NODE_END_DATE(new EndDateGroupByDto()),
  PROCESS_GROUP_BY_FLOW_NODE_START_DATE(new StartDateGroupByDto()),
  PROCESS_GROUP_BY_FLOW_NODE_DURATION(new DurationGroupByDto()),
  PROCESS_GROUP_BY_INCIDENT_FLOW_NODE(new FlowNodesGroupByDto()),
  PROCESS_GROUP_BY_PROCESS_INSTANCE_END_DATE(new EndDateGroupByDto()),
  PROCESS_GROUP_BY_PROCESS_INSTANCE_RUNNING_DATE(new RunningDateGroupByDto()),
  PROCESS_GROUP_BY_PROCESS_INSTANCE_START_DATE(new StartDateGroupByDto()),
  PROCESS_GROUP_BY_NONE(new NoneGroupByDto()),
  PROCESS_INCIDENT_GROUP_BY_NONE(new NoneGroupByDto()),
  PROCESS_GROUP_BY_USER_TASK(new UserTasksGroupByDto()),
  PROCESS_GROUP_BY_USER_TASK_DURATION(new DurationGroupByDto()),
  PROCESS_GROUP_BY_USER_TASK_END_DATE(new EndDateGroupByDto()),
  PROCESS_GROUP_BY_USER_TASK_START_DATE(new StartDateGroupByDto()),
  PROCESS_GROUP_BY_VARIABLE(new VariableGroupByDto());

  private final ProcessGroupByDto<?> dto;

  private ProcessGroupBy(final ProcessGroupByDto<?> dto) {
    this.dto = dto;
  }

  public ProcessGroupByDto<?> getDto() {
    return this.dto;
  }
}
