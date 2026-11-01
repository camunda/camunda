/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.AgentRunEnrichmentReader;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentRunEnrichmentServiceTest {

  @Mock private AgentRunEnrichmentReader reader;
  @Mock private AgentRunWriter agentRunWriter;
  @Mock private DatabaseClient databaseClient;
  @Mock private ConfigurationService configurationService;

  private AgentRunEnrichmentService underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new AgentRunEnrichmentService(reader, agentRunWriter, databaseClient, configurationService);
  }

  @Test
  void shouldIgnoreVariableAndNestedAgentImports() {
    // when
    underTest.onProcessInstancesImported(ValueType.VARIABLE.name(), List.of(processInstance()));
    underTest.onProcessInstancesImported(
        ValueType.AGENT_INSTANCE.name(), List.of(processInstance()));

    // then
    verifyNoInteractions(reader, databaseClient);
  }

  @Test
  void shouldCatchUpRunsOfImportedProcessInstances() {
    // given
    when(reader.getRunsByProcessInstanceIds(any())).thenReturn(List.of(run()));
    when(reader.getProcessInstances(Map.of("123", "loan")))
        .thenReturn(Map.of("123", processInstance()));
    when(agentRunWriter.generateEnrichmentImports(any())).thenReturn(List.of());

    // when
    underTest.onProcessInstancesImported(ValueType.INCIDENT.name(), List.of(processInstance()));

    // then
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<AgentRunDto>> enriched = ArgumentCaptor.forClass(List.class);
    verify(agentRunWriter).generateEnrichmentImports(enriched.capture());
    assertThat(enriched.getValue())
        .extracting(AgentRunDto::getAgentInstanceId)
        .containsExactly("700");
    verify(databaseClient)
        .executeImportRequestsAsBulk(eq("Agent run enrichment"), any(), anyBoolean());
  }

  @Test
  void shouldSkipRunsWhoseProcessInstanceIsNotImportedYet() {
    // given
    when(reader.getRunsByIds(anyCollection())).thenReturn(List.of(run()));
    when(reader.getProcessInstances(any())).thenReturn(Map.of());

    // when
    underTest.enrichRuns(List.of("700"));

    // then
    verify(databaseClient, never()).executeImportRequestsAsBulk(anyString(), any(), anyBoolean());
  }

  @Test
  void shouldNotFailTheImportWhenEnrichmentFails() {
    // given
    when(reader.getRunsByIds(anyCollection())).thenThrow(new IllegalStateException("db down"));

    // then
    assertThatNoException().isThrownBy(() -> underTest.enrichRuns(List.of("700")));
  }

  private static AgentRunDto run() {
    final AgentRunDto run = new AgentRunDto();
    run.setAgentInstanceId("700");
    run.setProcessInstanceId("123");
    run.setProcessDefinitionKey("loan");
    run.setFlowNodeId("review");
    return run;
  }

  private static ProcessInstanceDto processInstance() {
    final ProcessInstanceDto processInstance = new ProcessInstanceDto();
    processInstance.setProcessInstanceId("123");
    return processInstance;
  }
}
