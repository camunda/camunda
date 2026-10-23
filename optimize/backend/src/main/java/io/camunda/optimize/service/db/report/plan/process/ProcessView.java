/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.process;

import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.ADOPTION_RATE;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.AUTOMATION_RATE;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.DURATION;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.FREQUENCY;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.HUMAN_STEPS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.INCIDENT_RATE;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.INPUT_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.LIMIT_HIT_RATE;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.MODEL_CALLS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.OUTPUT_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.PERCENTAGE;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.TOOL_CALLS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.TOTAL_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity.AGENT_INSTANCE;
import static io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity.AGENT_RUN;
import static io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity.FLOW_NODE;
import static io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity.INCIDENT;
import static io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity.PROCESS_INSTANCE;
import static io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity.USER_TASK;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;

public enum ProcessView {
  PROCESS_VIEW_FLOW_NODE_DURATION(new ProcessViewDto(FLOW_NODE, DURATION)),
  PROCESS_VIEW_FLOW_NODE_FREQUENCY(new ProcessViewDto(FLOW_NODE, FREQUENCY)),
  PROCESS_VIEW_INCIDENT_DURATION(new ProcessViewDto(INCIDENT, DURATION)),
  PROCESS_VIEW_INCIDENT_FREQUENCY(new ProcessViewDto(INCIDENT, FREQUENCY)),
  PROCESS_VIEW_INSTANCE_DURATION(new ProcessViewDto(PROCESS_INSTANCE, DURATION)),
  PROCESS_VIEW_INSTANCE_DURATION_PROCESS_PART(
      new ProcessViewDto(PROCESS_INSTANCE, DURATION), new ProcessPartDto()),
  PROCESS_VIEW_INSTANCE_FREQUENCY(new ProcessViewDto(PROCESS_INSTANCE, FREQUENCY)),
  PROCESS_VIEW_INSTANCE_PERCENTAGE(new ProcessViewDto(PROCESS_INSTANCE, PERCENTAGE)),
  PROCESS_VIEW_INSTANCE_AUTOMATION_RATE(new ProcessViewDto(PROCESS_INSTANCE, AUTOMATION_RATE)),
  PROCESS_VIEW_RAW_DATA(new ProcessViewDto(ViewProperty.RAW_DATA)),
  PROCESS_VIEW_VARIABLE(
      new ProcessViewDto(ProcessViewEntity.VARIABLE, ViewProperty.variable(null, null))),
  PROCESS_VIEW_USER_TASK_FREQUENCY(new ProcessViewDto(USER_TASK, FREQUENCY)),
  PROCESS_VIEW_USER_TASK_DURATION(new ProcessViewDto(USER_TASK, DURATION)),
  PROCESS_VIEW_AGENT_INPUT_TOKENS(new ProcessViewDto(AGENT_INSTANCE, INPUT_TOKENS)),
  PROCESS_VIEW_AGENT_OUTPUT_TOKENS(new ProcessViewDto(AGENT_INSTANCE, OUTPUT_TOKENS)),
  PROCESS_VIEW_AGENT_TOOL_CALLS(new ProcessViewDto(AGENT_INSTANCE, TOOL_CALLS)),
  PROCESS_VIEW_AGENT_TOTAL_TOKENS(new ProcessViewDto(AGENT_INSTANCE, TOTAL_TOKENS)),
  PROCESS_VIEW_FLOW_NODE_TOOL_CALLS(new ProcessViewDto(FLOW_NODE, TOOL_CALLS)),
  // Agent run views read the flat agent-instance index, one document per run.
  PROCESS_VIEW_AGENT_RUN_FREQUENCY(new ProcessViewDto(AGENT_RUN, FREQUENCY)),
  PROCESS_VIEW_AGENT_RUN_DURATION(new ProcessViewDto(AGENT_RUN, DURATION)),
  PROCESS_VIEW_AGENT_RUN_TOTAL_TOKENS(new ProcessViewDto(AGENT_RUN, TOTAL_TOKENS)),
  PROCESS_VIEW_AGENT_RUN_MODEL_CALLS(new ProcessViewDto(AGENT_RUN, MODEL_CALLS)),
  PROCESS_VIEW_AGENT_RUN_HUMAN_STEPS(new ProcessViewDto(AGENT_RUN, HUMAN_STEPS)),
  PROCESS_VIEW_AGENT_RUN_TOOL_CALLS(new ProcessViewDto(AGENT_RUN, TOOL_CALLS)),
  PROCESS_VIEW_AGENT_RUN_INCIDENT_RATE(new ProcessViewDto(AGENT_RUN, INCIDENT_RATE)),
  PROCESS_VIEW_AGENT_RUN_LIMIT_HIT_RATE(new ProcessViewDto(AGENT_RUN, LIMIT_HIT_RATE)),
  PROCESS_VIEW_AGENT_RUN_ADOPTION_RATE(new ProcessViewDto(AGENT_RUN, ADOPTION_RATE));

  private final ProcessViewDto processViewDto;
  private final ProcessPartDto processPartDto;

  ProcessView(final ProcessViewDto processViewDto) {
    this.processViewDto = processViewDto;
    processPartDto = null;
  }

  private ProcessView(final ProcessViewDto processViewDto, final ProcessPartDto processPartDto) {
    this.processViewDto = processViewDto;
    this.processPartDto = processPartDto;
  }

  public boolean isFrequency() {
    return processViewDto.getFirstProperty().equals(FREQUENCY);
  }

  public ProcessViewDto getProcessViewDto() {
    return processViewDto;
  }

  public ProcessPartDto getProcessPartDto() {
    return processPartDto;
  }
}
