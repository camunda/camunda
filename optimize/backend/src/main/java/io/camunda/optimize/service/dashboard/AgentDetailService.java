/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessFailuresDto;
import io.camunda.optimize.service.db.reader.AgentDetailReader;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The single agent view and where a process's agents fail, with process names resolved. */
@Component
public class AgentDetailService {

  static final int MAX_REASONS_PER_AGENT = 3;

  private final AgentDetailReader reader;
  private final AgentInventoryService agentInventoryService;

  public AgentDetailService(
      final AgentDetailReader reader, final AgentInventoryService agentInventoryService) {
    this.reader = reader;
    this.agentInventoryService = agentInventoryService;
  }

  public Optional<AgentDetailDto> getAgentDetail(
      final String agentId, final OffsetDateTime endedFrom) {
    return reader
        .getAgentDetail(agentId, endedFrom)
        .map(
            agent ->
                agent.withProcessNames(
                    agentInventoryService.processName(agent.processDefinitionKey()),
                    agent.whereUsed().stream()
                        .map(
                            usage ->
                                usage.withRootProcessName(
                                    agentInventoryService.processName(
                                        usage.rootProcessDefinitionKey())))
                        .toList()));
  }

  public ProcessFailuresDto getProcessFailures(
      final String processDefinitionKey, final OffsetDateTime endedFrom) {
    return new ProcessFailuresDto(
        reader.getProcessFailures(processDefinitionKey, endedFrom, MAX_REASONS_PER_AGENT));
  }
}
