/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto.AgentInventoryEntry;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.AgentInventoryReader;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentInventoryServiceTest {

  private final AgentInventoryReader reader = mock(AgentInventoryReader.class);
  private final DefinitionService definitionService = mock(DefinitionService.class);
  private final AgentInventoryService underTest =
      new AgentInventoryService(reader, definitionService);

  @Test
  void shouldListBusiestAgentsFirstAndUnusedAgentsByName() {
    // given
    when(reader.getAgentInventory(any()))
        .thenReturn(
            List.of(
                entry("quiet", "Zeta agent", 0),
                entry("busy", "Beta agent", 40),
                entry("idle", "Alpha agent", 0),
                entry("medium", "Gamma agent", 7)));
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(any(), any()))
        .thenReturn(Optional.empty());

    // when
    final List<AgentInventoryEntry> agents = underTest.getAgentInventory(null).agents();

    // then
    assertThat(agents)
        .extracting(AgentInventoryEntry::agentId)
        .containsExactly("busy", "medium", "idle", "quiet");
  }

  @Test
  void shouldLabelEachAgentWithItsProcessNameAndFallBackToTheKey() {
    // given
    when(reader.getAgentInventory(any()))
        .thenReturn(List.of(entry("named", "Named", 1), entry("unnamed", "Unnamed", 1)));
    final ProcessDefinitionOptimizeDto loan = new ProcessDefinitionOptimizeDto();
    loan.setName("Loan Approval");
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            eq(DefinitionType.PROCESS), eq("process-named")))
        .thenReturn(Optional.of(loan));
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            eq(DefinitionType.PROCESS), eq("process-unnamed")))
        .thenReturn(Optional.empty());

    // when
    final List<AgentInventoryEntry> agents = underTest.getAgentInventory(null).agents();

    // then
    assertThat(agents)
        .extracting(AgentInventoryEntry::processDefinitionName)
        .containsExactly("Loan Approval", "process-unnamed");
  }

  private static AgentInventoryEntry entry(
      final String agentId, final String name, final long runs) {
    return new AgentInventoryEntry(
        agentId,
        name,
        "aiAgentSubProcess",
        "process-" + agentId,
        null,
        List.of("gpt-4o"),
        5L,
        runs,
        null,
        null,
        null,
        null,
        null);
  }
}
