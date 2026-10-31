/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto.AgentInventoryEntry;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto.Summary;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.AgentInventoryReader;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Lists agents for the agentic dashboard overview, busiest first, with process names. */
@Component
public class AgentInventoryService {

  private static final String EXTERNAL_AGENT = "EXTERNAL_AGENT";

  private final AgentInventoryReader reader;
  private final DefinitionService definitionService;

  public AgentInventoryService(
      final AgentInventoryReader reader, final DefinitionService definitionService) {
    this.reader = reader;
    this.definitionService = definitionService;
  }

  public AgentInventoryDto getAgentInventory(
      final OffsetDateTime endedFrom, final String processDefinitionKey) {
    final List<AgentInventoryEntry> agents =
        reader.getAgentInventory(endedFrom, processDefinitionKey).stream()
            .map(
                entry -> entry.withProcessDefinitionName(processName(entry.processDefinitionKey())))
            .sorted(
                Comparator.comparingLong(AgentInventoryEntry::runs)
                    .reversed()
                    .thenComparing(AgentInventoryEntry::name))
            .toList();
    return new AgentInventoryDto(summarize(agents), agents);
  }

  static Summary summarize(final List<AgentInventoryEntry> agents) {
    return new Summary(
        agents.size(),
        agents.stream()
            .map(AgentInventoryEntry::processDefinitionKey)
            .filter(Objects::nonNull)
            .distinct()
            .count(),
        agents.stream().filter(agent -> EXTERNAL_AGENT.equals(agent.agentType())).count());
  }

  /** The latest name of the process definition, or its key when it has none. */
  public String processName(final String processDefinitionKey) {
    return processDefinitionKey == null
        ? null
        : definitionService
            .getLatestCachedDefinitionOnAnyTenant(DefinitionType.PROCESS, processDefinitionKey)
            .map(DefinitionOptimizeResponseDto::getName)
            .filter(name -> !name.isBlank())
            .orElse(processDefinitionKey);
  }
}
