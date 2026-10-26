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
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.AgentInventoryReader;
import java.time.OffsetDateTime;
import java.util.Comparator;
import org.springframework.stereotype.Component;

/** Lists agents for the agentic dashboard overview, busiest first, with process names. */
@Component
public class AgentInventoryService {

  private final AgentInventoryReader reader;
  private final DefinitionService definitionService;

  public AgentInventoryService(
      final AgentInventoryReader reader, final DefinitionService definitionService) {
    this.reader = reader;
    this.definitionService = definitionService;
  }

  public AgentInventoryDto getAgentInventory(final OffsetDateTime endedFrom) {
    return new AgentInventoryDto(
        reader.getAgentInventory(endedFrom).stream()
            .map(this::withProcessName)
            .sorted(
                Comparator.comparingLong(AgentInventoryEntry::runs)
                    .reversed()
                    .thenComparing(AgentInventoryEntry::name))
            .toList());
  }

  private AgentInventoryEntry withProcessName(final AgentInventoryEntry entry) {
    final String processName =
        entry.processDefinitionKey() == null
            ? null
            : definitionService
                .getLatestCachedDefinitionOnAnyTenant(
                    DefinitionType.PROCESS, entry.processDefinitionKey())
                .map(DefinitionOptimizeResponseDto::getName)
                .filter(name -> !name.isBlank())
                .orElse(entry.processDefinitionKey());
    return new AgentInventoryEntry(
        entry.agentId(),
        entry.name(),
        entry.agentType(),
        entry.processDefinitionKey(),
        processName,
        entry.models(),
        entry.maxModelCalls(),
        entry.runs(),
        entry.incidentRate(),
        entry.limitHitRate(),
        entry.durationP50Ms(),
        entry.avgTokens(),
        entry.lastRunDate());
  }
}
