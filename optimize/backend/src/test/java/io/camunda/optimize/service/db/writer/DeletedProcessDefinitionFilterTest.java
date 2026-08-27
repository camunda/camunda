/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.GlobalCacheConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedProcessDefinitionFilterTest {

  private static final int TEST_MAX_SIZE = 3;

  private JobRegistryReader jobRegistryReader;
  private DeletedProcessDefinitionFilter filter;

  @BeforeEach
  void setUp() {
    jobRegistryReader = mock(JobRegistryReader.class);
    final CacheConfiguration cacheConfig = new CacheConfiguration();
    cacheConfig.setMaxSize(TEST_MAX_SIZE);
    cacheConfig.setDefaultTtlMillis(300_000);
    final GlobalCacheConfiguration globalCacheConfiguration = mock(GlobalCacheConfiguration.class);
    when(globalCacheConfiguration.getDeletedProcessDefinitions()).thenReturn(cacheConfig);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.getCaches()).thenReturn(globalCacheConfiguration);
    filter = new DeletedProcessDefinitionFilter(jobRegistryReader, configurationService);
  }

  @Test
  void shouldReturnEmptySetForEmptyInputWithoutQueryingReader() {
    // when
    final Set<String> result = filter.suppressedDefinitionIds(List.of());

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(jobRegistryReader);
  }

  @Test
  void shouldReturnOnlyIdsThatHaveADeletionJobEntry() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("2"));
    final List<String> candidateIds = List.of("1", "2", "3");

    // when
    final Set<String> result = filter.suppressedDefinitionIds(candidateIds);

    // then
    assertThat(result).containsExactly("2");
  }

  @Test
  void shouldFetchTheSuppressedIdSetOnceAndReuseItAcrossLookups() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("1"));

    // when
    filter.suppressedDefinitionIds(List.of("1"));
    filter.suppressedDefinitionIds(List.of("2"));

    // then the whole set is fetched once and served from the cache for subsequent lookups
    verify(jobRegistryReader, times(1))
        .findNewestEntityIds(JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE);
  }

  @Test
  void shouldIgnoreNullProcessDefinitionIdsWithoutThrowing() {
    // given a batch containing an entry with no process definition id set
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("2"));
    final List<String> candidateIds = Arrays.asList("1", null, "2");

    // when
    final Set<String> result = filter.suppressedDefinitionIds(candidateIds);

    // then
    assertThat(result).containsExactly("2");
  }

  @Test
  void shouldReturnSameListForEmptyInputWithoutQueryingReader() {
    // given
    final List<String> entries = List.of();

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).isSameAs(entries);
    verifyNoInteractions(jobRegistryReader);
  }

  @Test
  void shouldReturnAllEntriesWhenNoneAreSuppressed() {
    // given
    final List<String> entries = List.of("1", "2");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldFilterOutOnlySuppressedEntries() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("2"));
    final List<String> entries = List.of("1", "2", "3");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).containsExactlyInAnyOrder("1", "3");
  }

  @Test
  void shouldReturnEmptyListWhenAllEntriesAreSuppressed() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("1"));
    final List<String> entries = List.of("1");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).isEmpty();
  }
}
