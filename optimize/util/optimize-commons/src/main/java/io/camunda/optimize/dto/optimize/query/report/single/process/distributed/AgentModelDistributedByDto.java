/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.distributed;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.NoneDistributedByValueDto;

/** Agent runs split by the model the run used. Only valid for agent run views. */
public class AgentModelDistributedByDto
    extends ProcessReportDistributedByDto<NoneDistributedByValueDto> {

  public AgentModelDistributedByDto() {
    type = DistributedByType.AGENT_MODEL;
  }
}
