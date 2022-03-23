/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.AssigneeDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.CandidateGroupDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.EndDateDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.FlowNodeDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.StartDateDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.UserTaskDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.VariableDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.VariableDistributedByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessDistributedByCreator {

  public static NoneDistributedByDto createDistributedByNone() {
    return new NoneDistributedByDto();
  }

  public static AssigneeDistributedByDto createDistributedByAssignee() {
    return new AssigneeDistributedByDto();
  }

  public static CandidateGroupDistributedByDto createDistributedByCandidateGroup() {
    return new CandidateGroupDistributedByDto();
  }

  public static FlowNodeDistributedByDto createDistributedByFlowNode() {
    return new FlowNodeDistributedByDto();
  }

  public static UserTaskDistributedByDto createDistributedByUserTasks() {
    return new UserTaskDistributedByDto();
  }

  public static VariableDistributedByDto createDistributedByVariable(String variableName, VariableType variableType) {
    VariableDistributedByValueDto distributedByValueDto = new VariableDistributedByValueDto();
    distributedByValueDto.setName(variableName);
    distributedByValueDto.setType(variableType);
    VariableDistributedByDto distributedByDto = new VariableDistributedByDto();
    distributedByDto.setValue(distributedByValueDto);
    return distributedByDto;
  }

  public static StartDateDistributedByDto createDistributedByStartDateDto(AggregateByDateUnit dateInterval) {
    StartDateDistributedByDto distributedBy = new StartDateDistributedByDto();
    DateDistributedByValueDto distributedByValueDto = new DateDistributedByValueDto();
    distributedByValueDto.setUnit(dateInterval);
    distributedBy.setValue(distributedByValueDto);
    return distributedBy;
  }

  public static EndDateDistributedByDto createDistributedByEndDateDto(AggregateByDateUnit dateInterval) {
    EndDateDistributedByDto distributedBy = new EndDateDistributedByDto();
    DateDistributedByValueDto distributedByValueDto = new DateDistributedByValueDto();
    distributedByValueDto.setUnit(dateInterval);
    distributedBy.setValue(distributedByValueDto);
    return distributedBy;
  }
}
