/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.AgentRunEnrichmentReader;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  void shouldEnrichCompletedRunsWithTheirProcessInstanceInOneBulk() {
    // given
    when(reader.getRunsByIds(List.of("700", "701"))).thenReturn(List.of(run("700"), run("701")));
    when(reader.getProcessInstances(Map.of("123", "loan")))
        .thenReturn(Map.of("123", processInstance()));
    when(agentRunWriter.generateEnrichmentImports(any())).thenReturn(List.of());

    // when
    underTest.enrichCompletedRuns(List.of("700", "701"));

    // then
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<AgentRunDto>> enriched = ArgumentCaptor.forClass(List.class);
    verify(agentRunWriter).generateEnrichmentImports(enriched.capture());
    assertThat(enriched.getValue())
        .extracting(AgentRunDto::getAgentInstanceId, AgentRunDto::getEnriched)
        .containsExactly(tuple("700", true), tuple("701", true));
    verify(databaseClient)
        .executeImportRequestsAsBulk(eq("Agent run enrichment"), any(), anyBoolean());
    verify(reader, never()).getCompletedRunsByProcessInstanceIds(any());
  }

  @Test
  void shouldNeverEnrichARunOfARunningProcessInstance() {
    // given a stored run without end date
    final AgentRunDto running = run("700");
    running.setEndDate(null);
    when(reader.getRunsByIds(anyCollection())).thenReturn(List.of(running));

    // when
    underTest.enrichCompletedRuns(List.of("700"));

    // then
    verify(reader, never()).getProcessInstances(any());
    verifyNoInteractions(databaseClient);
  }

  @Test
  void shouldLookUpRootProcessesOncePerBatchAndOnlyForCalledProcesses() {
    // given two runs called from root 99 and one run in its own root process instance
    final AgentRunDto first = run("700");
    first.setRootProcessInstanceId("99");
    final AgentRunDto second = run("701");
    second.setRootProcessInstanceId("99");
    final AgentRunDto own = run("702");
    own.setRootProcessInstanceId("123");
    when(reader.getRunsByIds(anyCollection())).thenReturn(List.of(first, second, own));
    when(reader.getProcessInstances(any())).thenReturn(Map.of("123", processInstance()));
    when(reader.getProcessDefinitionKeys(Set.of("99"))).thenReturn(Map.of("99", "order"));
    when(agentRunWriter.generateEnrichmentImports(any())).thenReturn(List.of());

    // when
    underTest.enrichCompletedRuns(List.of("700", "701", "702"));

    // then
    verify(reader, times(1)).getProcessDefinitionKeys(any());
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<AgentRunDto>> enriched = ArgumentCaptor.forClass(List.class);
    verify(agentRunWriter).generateEnrichmentImports(enriched.capture());
    assertThat(enriched.getValue())
        .extracting(AgentRunDto::getRootProcessDefinitionKey)
        .containsExactly("order", "order", "loan");
  }

  @Test
  void shouldFailTheImportJobWhenEnrichmentFailsSoThePageIsRetried() {
    // given
    when(reader.getRunsByIds(anyCollection())).thenThrow(new IllegalStateException("db down"));

    // then
    assertThatThrownBy(() -> underTest.enrichCompletedRuns(List.of("700")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldReEnrichRunsOfAFinishedAgenticInstanceAfterALateIncident() {
    // given
    when(reader.getAgenticProcessDefinitionKeys()).thenReturn(Set.of("loan"));
    final ProcessInstanceDto finished = processInstance();
    finished.setEndDate(OffsetDateTime.now());
    when(reader.getProcessInstances(Map.of("123", "loan"))).thenReturn(Map.of("123", finished));
    when(reader.getCompletedRunsByProcessInstanceIds(Set.of("123")))
        .thenReturn(List.of(run("700")));
    when(agentRunWriter.generateEnrichmentImports(any())).thenReturn(List.of());

    // when
    underTest.reEnrichAfterLateIncidents(Map.of("123", "loan"));

    // then
    verify(agentRunWriter).generateEnrichmentImports(any());
    verify(databaseClient)
        .executeImportRequestsAsBulk(eq("Agent run enrichment"), any(), anyBoolean());
  }

  @Test
  void shouldNotReEnrichRunsOfARunningInstanceAfterALateIncident() {
    // given
    when(reader.getAgenticProcessDefinitionKeys()).thenReturn(Set.of("loan"));
    when(reader.getProcessInstances(any())).thenReturn(Map.of("123", processInstance()));

    // when
    underTest.reEnrichAfterLateIncidents(Map.of("123", "loan"));

    // then
    verify(reader, never()).getCompletedRunsByProcessInstanceIds(any());
    verifyNoInteractions(databaseClient);
  }

  @Test
  void shouldIgnoreLateIncidentsOfProcessesWithoutAgents() {
    // given
    when(reader.getAgenticProcessDefinitionKeys()).thenReturn(Set.of("loan"));
    underTest.addAgenticProcesses(List.of("chat"));

    // when
    underTest.reEnrichAfterLateIncidents(Map.of("555", "invoice"));

    // then
    verify(reader, never()).getProcessInstances(any());
    verify(reader, never()).getCompletedRunsByProcessInstanceIds(any());
    verifyNoInteractions(databaseClient);
  }

  @Test
  void shouldTreatProcessesSeenOnRunsAsAgentic() {
    // given a process with runs but no agent definition yet
    when(reader.getAgenticProcessDefinitionKeys()).thenReturn(Set.of());
    underTest.addAgenticProcesses(List.of("chat"));
    when(reader.getProcessInstances(any())).thenReturn(Map.of());

    // when
    underTest.reEnrichAfterLateIncidents(Map.of("123", "chat"));

    // then
    verify(reader).getProcessInstances(Map.of("123", "chat"));
  }

  private static AgentRunDto run(final String agentInstanceId) {
    final AgentRunDto run = new AgentRunDto();
    run.setAgentInstanceId(agentInstanceId);
    run.setEndDate(OffsetDateTime.now());
    run.setProcessInstanceId("123");
    run.setProcessDefinitionKey("loan");
    run.setFlowNodeId("review");
    return run;
  }

  private static ProcessInstanceDto processInstance() {
    final ProcessInstanceDto processInstance = new ProcessInstanceDto();
    processInstance.setProcessInstanceId("123");
    processInstance.setProcessDefinitionKey("loan");
    processInstance.setFlowNodeInstances(new java.util.ArrayList<>());
    processInstance.setIncidents(new java.util.ArrayList<>());
    return processInstance;
  }
}
