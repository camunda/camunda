/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.ONBOARDED;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.writer.DeletedProcessDefinitionFilter;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.GlobalCacheConfiguration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessDefinitionWriterESTest {

  private static final int TEST_MAX_SIZE = 10;

  private OptimizeElasticsearchClient esClient;
  private ConfigurationService configurationService;
  private TaskRepositoryES taskRepositoryES;
  private JobRegistryReader jobRegistryReader;
  private ProcessDefinitionWriterES writer;

  @BeforeEach
  void setUp() {
    esClient = mock(OptimizeElasticsearchClient.class);
    configurationService = mock(ConfigurationService.class);
    taskRepositoryES = mock(TaskRepositoryES.class);
    jobRegistryReader = mock(JobRegistryReader.class);
    final CacheConfiguration cacheConfig = new CacheConfiguration();
    cacheConfig.setMaxSize(TEST_MAX_SIZE);
    cacheConfig.setDefaultTtlMillis(300_000);
    final GlobalCacheConfiguration globalCacheConfiguration = mock(GlobalCacheConfiguration.class);
    when(globalCacheConfiguration.getDeletedProcessDefinitions()).thenReturn(cacheConfig);
    when(configurationService.getCaches()).thenReturn(globalCacheConfiguration);
    writer =
        new ProcessDefinitionWriterES(
            esClient,
            new ObjectMapper(),
            configurationService,
            taskRepositoryES,
            new DeletedProcessDefinitionFilter(jobRegistryReader, configurationService));
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
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("deletedDefinitionId"));

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
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("1"));

    // when
    writer.importProcessDefinitions(List.of(definitionA));

    // then
    final ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(esClient)
        .doImportBulkRequestWithList(anyString(), captor.capture(), any(), anyBoolean());
    assertThat(captor.getValue()).isEmpty();
  }

  @Test
  void shouldOnlyMarkNotYetOnboardedAndNotDeletedVersionsAsOnboardedInBoundedBatches() {
    // given
    final Set<String> keys = Set.of("onboardingKeyA", "onboardingKeyB");

    // when
    writer.markDefinitionKeysAsOnboarded(keys);

    // then
    final ArgumentCaptor<Script> scriptCaptor = ArgumentCaptor.forClass(Script.class);
    final ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(taskRepositoryES)
        .tryUpdateByQueryRequest(
            anyString(),
            scriptCaptor.capture(),
            queryCaptor.capture(),
            eq(false),
            eq(50),
            eq(PROCESS_DEFINITION_INDEX_NAME));

    final List<Query> mustClauses = queryCaptor.getValue().bool().must();
    assertThat(mustClauses).hasSize(3);
    assertThat(mustClauses)
        .anySatisfy(
            clause -> {
              assertThat(clause.isTerms()).isTrue();
              assertThat(clause.terms().field()).isEqualTo(PROCESS_DEFINITION_KEY);
              assertThat(clause.terms().terms().value())
                  .extracting(FieldValue::stringValue)
                  .containsExactlyInAnyOrderElementsOf(keys);
            });
    assertThat(mustClauses)
        .anySatisfy(
            clause -> {
              assertThat(clause.isTerm()).isTrue();
              assertThat(clause.term().field()).isEqualTo(ONBOARDED);
              assertThat(clause.term().value().booleanValue()).isFalse();
            });
    assertThat(mustClauses)
        .anySatisfy(
            clause -> {
              assertThat(clause.isTerm()).isTrue();
              assertThat(clause.term().field()).isEqualTo(DEFINITION_DELETED);
              assertThat(clause.term().value().booleanValue()).isFalse();
            });
    assertThat(scriptCaptor.getValue().source()).isEqualTo("ctx._source.onboarded = true");
  }

  private ProcessDefinitionOptimizeDto definition(final String id) {
    final ProcessDefinitionOptimizeDto dto = new ProcessDefinitionOptimizeDto();
    dto.setId(id);
    dto.setKey("someKey");
    return dto;
  }
}
