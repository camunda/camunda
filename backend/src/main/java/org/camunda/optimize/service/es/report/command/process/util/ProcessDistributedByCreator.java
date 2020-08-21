/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.AssigneeDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.CandidateGroupDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.FlowNodeDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.UserTaskDistributedByDto;

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
}
