/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.process;

import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.DURATION;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.FREQUENCY;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.PERCENTAGE;
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
  PROCESS_VIEW_RAW_DATA(new ProcessViewDto(ViewProperty.RAW_DATA)),
  PROCESS_VIEW_VARIABLE(
      new ProcessViewDto(ProcessViewEntity.VARIABLE, ViewProperty.variable(null, null))),
  PROCESS_VIEW_USER_TASK_FREQUENCY(new ProcessViewDto(USER_TASK, FREQUENCY)),
  PROCESS_VIEW_USER_TASK_DURATION(new ProcessViewDto(USER_TASK, DURATION));

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
