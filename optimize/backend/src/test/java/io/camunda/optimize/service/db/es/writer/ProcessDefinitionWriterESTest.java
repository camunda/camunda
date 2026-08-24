/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.writer.DeletedProcessDefinitionCache;
import io.camunda.optimize.service.db.writer.DeletedProcessDefinitionFilter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessDefinitionWriterESTest {

  private OptimizeElasticsearchClient esClient;
  private ConfigurationService configurationService;
  private TaskRepositoryES taskRepositoryES;
  private DeletedProcessDefinitionCache deletedProcessDefinitionCache;
  private ProcessDefinitionWriterES writer;

  @BeforeEach
  void setUp() {
    esClient = mock(OptimizeElasticsearchClient.class);
    configurationService = mock(ConfigurationService.class);
    taskRepositoryES = mock(TaskRepositoryES.class);
    deletedProcessDefinitionCache = mock(DeletedProcessDefinitionCache.class);
    writer =
        new ProcessDefinitionWriterES(
            esClient,
            new ObjectMapper(),
            configurationService,
            taskRepositoryES,
            new DeletedProcessDefinitionFilter(deletedProcessDefinitionCache));
  }

  @Test
  void shouldImportAllDefinitionsWhenNoDeletionJobEntryExists() {
    // given
    final ProcessDefinitionOptimizeDto definitionA = definition("1");
    final ProcessDefinitionOptimizeDto definitionB = definition("2");

    // when
    writer.importProcessDefinitions(List.of(definitionA, definitionB));

    // then
    final ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(esClient)
        .doImportBulkRequestWithList(anyString(), captor.capture(), any(), anyBoolean());
    assertThat(captor.getValue()).containsExactlyInAnyOrder(definitionA, definitionB);
  }

  @Test
  void shouldSuppressOnlyMatchingDefinition() {
    // given
    final ProcessDefinitionOptimizeDto suppressed = definition("deletedDefinitionId");
    final ProcessDefinitionOptimizeDto kept = definition("keptDefinitionId");
    when(deletedProcessDefinitionCache.isSuppressed("deletedDefinitionId")).thenReturn(true);

    // when
    writer.importProcessDefinitions(List.of(suppressed, kept));

    // then
    final ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(esClient)
        .doImportBulkRequestWithList(anyString(), captor.capture(), any(), anyBoolean());
    assertThat(captor.getValue()).containsExactly(kept);
  }

  @Test
  void shouldWriteNothingWhenAllDefinitionsAreDeleted() {
    // given
    final ProcessDefinitionOptimizeDto definitionA = definition("1");
    when(deletedProcessDefinitionCache.isSuppressed("1")).thenReturn(true);

    // when
    writer.importProcessDefinitions(List.of(definitionA));

    // then
    final ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(esClient)
        .doImportBulkRequestWithList(anyString(), captor.capture(), any(), anyBoolean());
    assertThat(captor.getValue()).isEmpty();
  }

  private ProcessDefinitionOptimizeDto definition(final String id) {
    final ProcessDefinitionOptimizeDto dto = new ProcessDefinitionOptimizeDto();
    dto.setId(id);
    dto.setKey("someKey");
    return dto;
  }
}
