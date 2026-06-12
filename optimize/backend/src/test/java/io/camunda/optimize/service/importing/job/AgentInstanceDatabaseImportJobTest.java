/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentInstanceDatabaseImportJobTest {

  @Mock private ProcessInstanceWriter processInstanceWriter;
  @Mock private ConfigurationService configurationService;
  @Mock private DatabaseClient databaseClient;
  @Mock private ProcessDefinitionWriter processDefinitionWriter;

  // Use a real cache instance (not a mock) so behavior matches production exactly.
  private AgenticProcessFlagCache agenticProcessFlagCache;
  private AgentInstanceDatabaseImportJob job;

  @BeforeEach
  void setUp() {
    agenticProcessFlagCache = new AgenticProcessFlagCache();
    job =
        new AgentInstanceDatabaseImportJob(
            processInstanceWriter,
            configurationService,
            () -> {},
            "agent-instance",
            databaseClient,
            processDefinitionWriter,
            agenticProcessFlagCache);
  }

  @Test
  void shouldFlipFlagForNewDefinitionIds() {
    // given
    final List<ProcessInstanceDto> instances = List.of(instance("def-1"), instance("def-2"));

    // when
    job.flipAgenticProcessFlagIfNeeded(instances);

    // then
    verify(processDefinitionWriter)
        .markDefinitionsAsAgenticProcesses(java.util.Set.of("def-1", "def-2"));
    assertThat(agenticProcessFlagCache.filterUnflipped(List.of("def-1", "def-2"))).isEmpty();
  }

  @Test
  void shouldOnlyFlipIdsNotYetInCache() {
    // given
    agenticProcessFlagCache.markFlipped(List.of("def-1"));
    final List<ProcessInstanceDto> instances = List.of(instance("def-1"), instance("def-2"));

    // when
    job.flipAgenticProcessFlagIfNeeded(instances);

    // then — only the new id is flipped
    verify(processDefinitionWriter).markDefinitionsAsAgenticProcesses(java.util.Set.of("def-2"));
    assertThat(agenticProcessFlagCache.filterUnflipped(List.of("def-1", "def-2"))).isEmpty();
  }

  @Test
  void shouldNotCallWriterWhenAllIdsAlreadyFlipped() {
    // given
    agenticProcessFlagCache.markFlipped(List.of("def-1", "def-2"));
    final List<ProcessInstanceDto> instances = List.of(instance("def-1"), instance("def-2"));

    // when
    job.flipAgenticProcessFlagIfNeeded(instances);

    // then
    verifyNoInteractions(processDefinitionWriter);
  }

  @Test
  void shouldNotPersistFlipWhenWriterFails() {
    // given
    doThrow(new RuntimeException("ES down"))
        .when(processDefinitionWriter)
        .markDefinitionsAsAgenticProcesses(anySet());

    // when — no exception bubbles up
    job.flipAgenticProcessFlagIfNeeded(List.of(instance("def-1")));

    // then — cache stays empty so the next batch retries
    assertThat(agenticProcessFlagCache.filterUnflipped(List.of("def-1"))).containsExactly("def-1");
  }

  @Test
  void shouldSkipFlipWhenDefinitionIdIsNull() {
    // given
    final ProcessInstanceDto withoutId = new ProcessInstanceDto();
    withoutId.setProcessDefinitionId(null);

    // when
    job.flipAgenticProcessFlagIfNeeded(List.of(withoutId));

    // then
    verify(processDefinitionWriter, never()).markDefinitionsAsAgenticProcesses(any());
  }

  @Test
  void shouldFlipDistinctIdsForDifferentVersionsOfSameKey() {
    // given — two versions of the same BPMN key produce two distinct definition IDs
    final ProcessInstanceDto v1 = new ProcessInstanceDto();
    v1.setProcessDefinitionKey("invoice-process");
    v1.setProcessDefinitionId("invoice-process:1");
    final ProcessInstanceDto v2 = new ProcessInstanceDto();
    v2.setProcessDefinitionKey("invoice-process");
    v2.setProcessDefinitionId("invoice-process:2");

    // when
    job.flipAgenticProcessFlagIfNeeded(List.of(v1, v2));

    // then — both versions get flipped independently
    verify(processDefinitionWriter)
        .markDefinitionsAsAgenticProcesses(
            java.util.Set.of("invoice-process:1", "invoice-process:2"));
  }

  @Test
  void shouldNotCallWriterWhenInputIsEmpty() {
    // when
    job.flipAgenticProcessFlagIfNeeded(List.of());

    // then
    verifyNoInteractions(processDefinitionWriter);
  }

  private ProcessInstanceDto instance(final String definitionId) {
    final ProcessInstanceDto dto = new ProcessInstanceDto();
    dto.setProcessDefinitionId(definitionId);
    return dto;
  }
}
