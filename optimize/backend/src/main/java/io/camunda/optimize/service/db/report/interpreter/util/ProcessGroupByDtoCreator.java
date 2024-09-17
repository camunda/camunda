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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessGroupByDtoCreator {

  public static StartDateGroupByDto createGroupByStartDateDto(AggregateByDateUnit dateInterval) {
    StartDateGroupByDto groupByDto = new StartDateGroupByDto();
    DateGroupByValueDto valueDto = new DateGroupByValueDto();
    valueDto.setUnit(dateInterval);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static EndDateGroupByDto createGroupByEndDateDto(AggregateByDateUnit dateInterval) {
    EndDateGroupByDto groupByDto = new EndDateGroupByDto();
    DateGroupByValueDto valueDto = new DateGroupByValueDto();
    valueDto.setUnit(dateInterval);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static EndDateGroupByDto createGroupByEndDateDto() {
    return createGroupByEndDateDto(null);
  }

  public static RunningDateGroupByDto createGroupByRunningDateDto(
      AggregateByDateUnit dateInterval) {
    RunningDateGroupByDto groupByDto = new RunningDateGroupByDto();
    DateGroupByValueDto valueDto = new DateGroupByValueDto();
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
      String variableName, VariableType variableType) {
    VariableGroupByValueDto groupByValueDto = new VariableGroupByValueDto();
    groupByValueDto.setName(variableName);
    groupByValueDto.setType(variableType);
    VariableGroupByDto groupByDto = new VariableGroupByDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }

  public static VariableGroupByDto createGroupByVariable() {
    return createGroupByVariable(null, null);
  }
}
