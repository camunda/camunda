/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.AssigneeDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.CandidateGroupDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.EndDateDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.FlowNodeDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.StartDateDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.UserTaskDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.VariableDistributedByDto;

public enum ProcessDistributedBy {
  PROCESS_DISTRIBUTED_BY_ASSIGNEE(new AssigneeDistributedByDto()),
  PROCESS_DISTRIBUTED_BY_CANDIDATE_GROUP(new CandidateGroupDistributedByDto()),
  PROCESS_DISTRIBUTED_BY_FLOW_NODE(new FlowNodeDistributedByDto()),
  PROCESS_DISTRIBUTED_BY_INSTANCE_END_DATE(new EndDateDistributedByDto()),
  PROCESS_DISTRIBUTED_BY_INSTANCE_START_DATE(new StartDateDistributedByDto()),
  PROCESS_DISTRIBUTED_BY_NONE(new NoneDistributedByDto()),
  PROCESS_DISTRIBUTED_BY_PROCESS(new ProcessDistributedByDto()),
  PROCESS_DISTRIBUTED_BY_USER_TASK(new UserTaskDistributedByDto()),
  PROCESS_DISTRIBUTED_BY_VARIABLE(new VariableDistributedByDto());

  private final ProcessReportDistributedByDto<?> dto;

  private ProcessDistributedBy(final ProcessReportDistributedByDto<?> dto) {
    this.dto = dto;
  }

  public ProcessReportDistributedByDto<?> getDto() {
    return this.dto;
  }
}
