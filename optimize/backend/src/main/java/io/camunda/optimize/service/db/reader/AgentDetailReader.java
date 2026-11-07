/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessFailuresDto.AgentFailures;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reads one agent for the single agent view and the failures of a process's agents. Runs are those
 * of completed process instances that ended at or after {@code endedFrom} (all runs when null).
 * Process names are left empty for the caller to resolve.
 */
public interface AgentDetailReader {

  /** Empty when the agent has neither a definition nor runs. */
  Optional<AgentDetailDto> getAgentDetail(String agentId, OffsetDateTime endedFrom);

  /** Agents of the process with at least one failed run, with their top reasons. */
  List<AgentFailures> getProcessFailures(
      String processDefinitionKey, OffsetDateTime endedFrom, int maxReasonsPerAgent);
}
