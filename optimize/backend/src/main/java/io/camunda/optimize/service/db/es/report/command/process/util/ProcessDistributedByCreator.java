/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.process.util;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.AssigneeDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.CandidateGroupDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.EndDateDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.FlowNodeDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.StartDateDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.UserTaskDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.VariableDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.VariableDistributedByValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public class ProcessDistributedByCreator {

  private ProcessDistributedByCreator() {}

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

  public static VariableDistributedByDto createDistributedByVariable(
      final String variableName, final VariableType variableType) {
    final VariableDistributedByValueDto distributedByValueDto = new VariableDistributedByValueDto();
    distributedByValueDto.setName(variableName);
    distributedByValueDto.setType(variableType);
    final VariableDistributedByDto distributedByDto = new VariableDistributedByDto();
    distributedByDto.setValue(distributedByValueDto);
    return distributedByDto;
  }

  public static StartDateDistributedByDto createDistributedByStartDateDto(
      final AggregateByDateUnit dateInterval) {
    final StartDateDistributedByDto distributedBy = new StartDateDistributedByDto();
    final DateDistributedByValueDto distributedByValueDto = new DateDistributedByValueDto();
    distributedByValueDto.setUnit(dateInterval);
    distributedBy.setValue(distributedByValueDto);
    return distributedBy;
  }

  public static EndDateDistributedByDto createDistributedByEndDateDto(
      final AggregateByDateUnit dateInterval) {
    final EndDateDistributedByDto distributedBy = new EndDateDistributedByDto();
    final DateDistributedByValueDto distributedByValueDto = new DateDistributedByValueDto();
    distributedByValueDto.setUnit(dateInterval);
    distributedBy.setValue(distributedByValueDto);
    return distributedBy;
  }
}
