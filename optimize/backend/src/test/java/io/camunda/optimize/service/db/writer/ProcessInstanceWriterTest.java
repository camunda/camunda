/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.helper.ImportRequestDtoFactory;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.GlobalCacheConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessInstanceWriterTest {

  private static final int TEST_MAX_SIZE = 10;

  private IndexRepository indexRepository;
  private ImportRequestDtoFactory importRequestDtoFactory;
  private ProcessInstanceRepository processInstanceRepository;
  private JobRegistryReader jobRegistryReader;
  private ProcessInstanceWriter writer;

  @BeforeEach
  void setUp() {
    indexRepository = mock(IndexRepository.class);
    importRequestDtoFactory = mock(ImportRequestDtoFactory.class);
    processInstanceRepository = mock(ProcessInstanceRepository.class);
    jobRegistryReader = mock(JobRegistryReader.class);
    final CacheConfiguration cacheConfig = new CacheConfiguration();
    cacheConfig.setMaxSize(TEST_MAX_SIZE);
    cacheConfig.setDefaultTtlMillis(300_000);
    final GlobalCacheConfiguration globalCacheConfiguration = mock(GlobalCacheConfiguration.class);
    when(globalCacheConfiguration.getDeletedProcessDefinitions()).thenReturn(cacheConfig);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.getCaches()).thenReturn(globalCacheConfiguration);
    writer =
        new ProcessInstanceWriter(
            indexRepository,
            new ObjectMapper(),
            importRequestDtoFactory,
            processInstanceRepository,
            new DeletedProcessDefinitionFilter(jobRegistryReader, configurationService));
    when(importRequestDtoFactory.createImportRequestForProcessInstance(
            any(ProcessInstanceDto.class), anySet(), any(String.class)))
        .thenAnswer(
            invocation ->
                ImportRequestDto.builder()
                    .id(((ProcessInstanceDto) invocation.getArgument(0)).getProcessInstanceId())
                    .build());
  }

  @Test
  void shouldImportNormallyWhenNoDeletionJobEntryExists() {
    // given
    final ProcessInstanceDto instanceA = instance("definitionKeyA", "definitionIdA", "instance-1");
    final ProcessInstanceDto instanceB = instance("definitionKeyB", "definitionIdB", "instance-2");

    // when
    final List<ImportRequestDto> requests =
        writer.generateProcessInstanceImports(List.of(instanceA, instanceB), "source-index");

    // then
    assertThat(requests).hasSize(2);
    final ArgumentCaptor<Set<String>> keysCaptor = ArgumentCaptor.forClass(Set.class);
    verify(indexRepository, times(1)).createMissingIndices(any(), anySet(), keysCaptor.capture());
    assertThat(keysCaptor.getValue()).containsExactlyInAnyOrder("definitionKeyA", "definitionKeyB");
  }

  @Test
  void shouldSuppressOnlyMatchingDefinitionInMixedBatch() {
    // given
    final ProcessInstanceDto suppressed =
        instance("sharedKey", "deletedDefinitionId", "instance-1");
    final ProcessInstanceDto kept = instance("sharedKey", "keptDefinitionId", "instance-2");
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("deletedDefinitionId"));

    // when
    final List<ImportRequestDto> requests =
        writer.generateProcessInstanceImports(List.of(suppressed, kept), "source-index");

    // then
    assertThat(requests)
        .singleElement()
        .satisfies(request -> assertThat(request.getId()).isEqualTo("instance-2"));
  }

  @Test
  void shouldSuppressEntireBatchWhenAllDefinitionsAreDeleted() {
    // given
    final ProcessInstanceDto instanceA = instance("definitionKeyA", "definitionIdA", "instance-1");
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("definitionIdA"));

    // when
    final List<ImportRequestDto> requests =
        writer.generateProcessInstanceImports(List.of(instanceA), "source-index");

    // then
    assertThat(requests).isEmpty();
    verify(indexRepository).createMissingIndices(any(), anySet(), eq(Set.of()));
  }

  @Test
  void shouldSuppressOnlyMatchingDefinitionForRunningProcessInstanceImports() {
    // given
    final ProcessInstanceDto suppressed =
        instance("sharedKey", "deletedDefinitionId", "instance-1");
    final ProcessInstanceDto kept = instance("sharedKey", "keptDefinitionId", "instance-2");
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("deletedDefinitionId"));

    // when
    final List<ImportRequestDto> requests =
        writer.generateRunningProcessInstanceImports(new ArrayList<>(List.of(suppressed, kept)));

    // then
    assertThat(requests)
        .singleElement()
        .satisfies(request -> assertThat(request.getId()).isEqualTo("instance-2"));
  }

  @Test
  void shouldSuppressOnlyMatchingDefinitionForCompletedProcessInstanceImports() {
    // given
    final ProcessInstanceDto suppressed =
        instance("sharedKey", "deletedDefinitionId", "instance-1");
    final ProcessInstanceDto kept = instance("sharedKey", "keptDefinitionId", "instance-2");
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("deletedDefinitionId"));

    // when
    final List<ImportRequestDto> requests =
        writer.generateCompletedProcessInstanceImports(new ArrayList<>(List.of(suppressed, kept)));

    // then
    assertThat(requests)
        .singleElement()
        .satisfies(request -> assertThat(request.getId()).isEqualTo("instance-2"));
  }

  @Test
  void shouldNeverFilterDeleteByIds() {
    // when
    writer.deleteByIds("someDefinitionKey", List.of("instance-1"));

    // then
    verifyNoInteractions(jobRegistryReader);
  }

  private ProcessInstanceDto instance(
      final String processDefinitionKey,
      final String processDefinitionId,
      final String instanceId) {
    final ProcessInstanceDto dto = new ProcessInstanceDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setProcessInstanceId(instanceId);
    return dto;
  }
}
