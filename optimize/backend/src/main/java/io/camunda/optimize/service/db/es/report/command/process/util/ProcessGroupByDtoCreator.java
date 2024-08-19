/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.process.util;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.AssigneeGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.CandidateGroupGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.RunningDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.UserTasksGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public class ProcessGroupByDtoCreator {

  private ProcessGroupByDtoCreator() {}

  public static StartDateGroupByDto createGroupByStartDateDto(
      final AggregateByDateUnit dateInterval) {
    final StartDateGroupByDto groupByDto = new StartDateGroupByDto();
    final DateGroupByValueDto valueDto = new DateGroupByValueDto();
    valueDto.setUnit(dateInterval);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static EndDateGroupByDto createGroupByEndDateDto(final AggregateByDateUnit dateInterval) {
    final EndDateGroupByDto groupByDto = new EndDateGroupByDto();
    final DateGroupByValueDto valueDto = new DateGroupByValueDto();
    valueDto.setUnit(dateInterval);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static EndDateGroupByDto createGroupByEndDateDto() {
    return createGroupByEndDateDto(null);
  }

  public static RunningDateGroupByDto createGroupByRunningDateDto(
      final AggregateByDateUnit dateInterval) {
    final RunningDateGroupByDto groupByDto = new RunningDateGroupByDto();
    final DateGroupByValueDto valueDto = new DateGroupByValueDto();
    valueDto.setUnit(dateInterval);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static RunningDateGroupByDto createGroupByRunningDateDto() {
    return createGroupByRunningDateDto(null);
  }

  public static FlowNodesGroupByDto createGroupByFlowNode() {
    return new FlowNodesGroupByDto();
  }

  public static UserTasksGroupByDto createGroupByUserTasks() {
    return new UserTasksGroupByDto();
  }

  public static AssigneeGroupByDto createGroupByAssignee() {
    return new AssigneeGroupByDto();
  }

  public static CandidateGroupGroupByDto createGroupByCandidateGroup() {
    return new CandidateGroupGroupByDto();
  }

  public static DurationGroupByDto createGroupByDuration() {
    return new DurationGroupByDto();
  }

  public static NoneGroupByDto createGroupByNone() {
    return new NoneGroupByDto();
  }

  public static VariableGroupByDto createGroupByVariable(
      final String variableName, final VariableType variableType) {
    final VariableGroupByValueDto groupByValueDto = new VariableGroupByValueDto();
    groupByValueDto.setName(variableName);
    groupByValueDto.setType(variableType);
    final VariableGroupByDto groupByDto = new VariableGroupByDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }

  public static VariableGroupByDto createGroupByVariable() {
    return createGroupByVariable(null, null);
  }
}
