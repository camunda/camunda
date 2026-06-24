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
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionKeyGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionVersionGroupByDto;
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
  PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY(new ProcessDefinitionKeyGroupByDto()),
  // Same group-by shape as PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY but routed to a dedicated
  // interpreter that supports server-side top-N limiting (used by the agentic top token consumers
  // tile). Reusing the same DTO keeps the report command key unchanged; the distinct enum value
  // only selects the interpreter, and the flag advertises that a limit-only pagination is allowed.
  PROCESS_GROUP_BY_AGENT_PROCESS_DEFINITION_KEY(new ProcessDefinitionKeyGroupByDto(), true),
  PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION(new ProcessDefinitionVersionGroupByDto()),
  PROCESS_GROUP_BY_NONE(new NoneGroupByDto()),
  PROCESS_INCIDENT_GROUP_BY_NONE(new NoneGroupByDto()),
  PROCESS_GROUP_BY_USER_TASK(new UserTasksGroupByDto()),
  PROCESS_GROUP_BY_USER_TASK_DURATION(new DurationGroupByDto()),
  PROCESS_GROUP_BY_USER_TASK_END_DATE(new EndDateGroupByDto()),
  PROCESS_GROUP_BY_USER_TASK_START_DATE(new StartDateGroupByDto()),
  PROCESS_GROUP_BY_VARIABLE(new VariableGroupByDto());

  private final ProcessGroupByDto<?> dto;
  private final boolean topNLimitSupported;

  private ProcessGroupBy(final ProcessGroupByDto<?> dto) {
    this(dto, false);
  }

  private ProcessGroupBy(final ProcessGroupByDto<?> dto, final boolean topNLimitSupported) {
    this.dto = dto;
    this.topNLimitSupported = topNLimitSupported;
  }

  public ProcessGroupByDto<?> getDto() {
    return this.dto;
  }

  public boolean isTopNLimitSupported() {
    return topNLimitSupported;
  }
}
