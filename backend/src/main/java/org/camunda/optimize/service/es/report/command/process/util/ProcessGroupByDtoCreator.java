/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;


public class ProcessGroupByDtoCreator {

  public static ProcessGroupByDto createGroupByStartDateDto(GroupByDateUnit dateInterval) {
    StartDateGroupByDto groupByDto = new StartDateGroupByDto();
    StartDateGroupByValueDto valueDto = new StartDateGroupByValueDto();
    valueDto.setUnit(dateInterval);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static ProcessGroupByDto createGroupByStartDateDto() {
    return createGroupByStartDateDto(null);
  }

  public static ProcessGroupByDto createGroupByFlowNode() {
    return new FlowNodesGroupByDto();
  }

  public static ProcessGroupByDto createGroupByNone() {
    return new NoneGroupByDto();
  }

  public static ProcessGroupByDto createGroupByVariable(String variableName, VariableType variableType) {
    VariableGroupByValueDto groupByValueDto = new VariableGroupByValueDto();
    groupByValueDto.setName(variableName);
    groupByValueDto.setType(variableType);
    VariableGroupByDto groupByDto = new VariableGroupByDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }

  public static ProcessGroupByDto createGroupByVariable() {
    return createGroupByVariable(null, null);
  }
}
