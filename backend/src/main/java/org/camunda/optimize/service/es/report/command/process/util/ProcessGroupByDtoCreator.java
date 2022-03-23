/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.AssigneeGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.CandidateGroupGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.RunningDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.UserTasksGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

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

  public static RunningDateGroupByDto createGroupByRunningDateDto(AggregateByDateUnit dateInterval) {
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

  public static VariableGroupByDto createGroupByVariable(String variableName, VariableType variableType) {
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
