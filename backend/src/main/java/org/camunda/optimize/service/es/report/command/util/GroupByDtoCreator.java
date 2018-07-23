package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.group.FlowNodesGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.group.value.VariableGroupByValueDto;


public class GroupByDtoCreator {

  public static GroupByDto createGroupByStartDateDto(String dateInterval) {
    StartDateGroupByDto groupByDto = new StartDateGroupByDto();
    StartDateGroupByValueDto valueDto = new StartDateGroupByValueDto();
    valueDto.setUnit(dateInterval);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static GroupByDto createGroupByStartDateDto() {
    return createGroupByStartDateDto(null);
  }

  public static GroupByDto createGroupByFlowNode() {
    return new FlowNodesGroupByDto();
  }

  public static GroupByDto createGroupByNone() {
    return new NoneGroupByDto();
  }

  public static GroupByDto createGroupByVariable(String variableName, String variableType) {
    VariableGroupByValueDto groupByValueDto = new VariableGroupByValueDto();
    groupByValueDto.setName(variableName);
    groupByValueDto.setType(variableType);
    VariableGroupByDto groupByDto = new VariableGroupByDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }

  public static GroupByDto createGroupByVariable() {
    return createGroupByVariable(null, null);
  }
}
