/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.writer.DeletedProcessDefinitionFilter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessDefinitionWriterOSTest {

  private OptimizeOpenSearchClient osClient;
  private ConfigurationService configurationService;
  private JobRegistryReader jobRegistryReader;
  private ProcessDefinitionWriterOS writer;

  @BeforeEach
  void setUp() {
    osClient = mock(OptimizeOpenSearchClient.class);
    configurationService = mock(ConfigurationService.class);
    jobRegistryReader = mock(JobRegistryReader.class);
    writer =
        new ProcessDefinitionWriterOS(
            osClient,
            new ObjectMapper(),
            configurationService,
            new DeletedProcessDefinitionFilter(jobRegistryReader));
  }

  @Test
  void shouldImportAllDefinitionsWhenNoDeletionJobEntryExists() {
    // given
    when(jobRegistryReader.findEntityIdsWithJob(any(), any(), anyCollection()))
        .thenReturn(Set.of());
    final ProcessDefinitionOptimizeDto definitionA = definition("1");
    final ProcessDefinitionOptimizeDto definitionB = definition("2");

    // when
    writer.importProcessDefinitions(List.of(definitionA, definitionB));

    // then
    final ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(osClient)
        .doImportBulkRequestWithList(
            anyString(), captor.capture(), any(), anyBoolean(), anyString());
    assertThat(captor.getValue()).containsExactlyInAnyOrder(definitionA, definitionB);
  }

  @Test
  void shouldSuppressOnlyMatchingDefinition() {
    // given
    final ProcessDefinitionOptimizeDto suppressed = definition("deletedDefinitionId");
    final ProcessDefinitionOptimizeDto kept = definition("keptDefinitionId");
    when(jobRegistryReader.findEntityIdsWithJob(any(), any(), anyCollection()))
        .thenReturn(Set.of("deletedDefinitionId"));

    // when
    writer.importProcessDefinitions(List.of(suppressed, kept));

    // then
    final ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(osClient)
        .doImportBulkRequestWithList(
            anyString(), captor.capture(), any(), anyBoolean(), anyString());
    assertThat(captor.getValue()).containsExactly(kept);
  }

  @Test
  void shouldWriteNothingWhenAllDefinitionsAreDeleted() {
    // given
    final ProcessDefinitionOptimizeDto definitionA = definition("1");
    when(jobRegistryReader.findEntityIdsWithJob(any(), any(), anyCollection()))
        .thenReturn(Set.of("1"));

    // when
    writer.importProcessDefinitions(List.of(definitionA));

    // then
    final ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(osClient)
        .doImportBulkRequestWithList(
            anyString(), captor.capture(), any(), anyBoolean(), anyString());
    assertThat(captor.getValue()).isEmpty();
  }

  private ProcessDefinitionOptimizeDto definition(final String id) {
    final ProcessDefinitionOptimizeDto dto = new ProcessDefinitionOptimizeDto();
    dto.setId(id);
    dto.setKey("someKey");
    return dto;
  }
}
