/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.agentic;

import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto.FailureReason;
import java.util.List;

/** Where the agents of one process fail, agents with the most failed runs first. */
public record ProcessFailuresDto(List<AgentFailures> agents) {

  /** One agent with failed runs and its top reasons. */
  public record AgentFailures(
      String agentId,
      String name,
      long runs,
      long failedRuns,
      Double successRate,
      List<FailureReason> topReasons) {}
}
